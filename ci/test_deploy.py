import hashlib
import importlib.util
import io
import os
from pathlib import Path
import tarfile
import tempfile
import types
import unittest
from unittest.mock import patch, Mock

spec = importlib.util.spec_from_file_location('deploy', Path(__file__).with_name('deploy.py'))
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)


class DeploymentTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.site = self.root / 'site'
        self.site.mkdir()
        self.live = self.site / 'index'
        self.live.mkdir()
        (self.live / 'index.html').write_text('old frontend')
        self.incoming = self.root / 'incoming'
        self.incoming.mkdir()
        self.jar = self.root / 'app.jar'
        self.jar.write_bytes(b'old backend')
        self.config = self.root / 'application-prod.properties'
        self.config.write_text('old.secret=value\n')
        self.config.chmod(0o600)
        self.paths = patch.multiple(deploy, SITE=self.site, FRONTEND_RELEASES=self.site / 'releases',
                                    INCOMING=self.incoming, BACKEND_RELEASES=self.root / 'backend-releases',
                                    LIVE_JAR=self.jar, ACTIVE_CONFIG=self.config)
        self.paths.start()
        self.account = patch.object(deploy.pwd, 'getpwnam', return_value=types.SimpleNamespace(
            pw_uid=os.getuid(), pw_gid=os.getgid()))
        self.account.start()

    def tearDown(self):
        self.account.stop()
        self.paths.stop()
        self.temp.cleanup()

    def archive(self, name='index.html', content=b'new frontend', kind=None, assets=()):
        path = self.incoming / 'frontend.tgz'
        with tarfile.open(path, 'w:gz') as archive:
            member = tarfile.TarInfo(name)
            if kind:
                member.type = kind
                member.linkname = '/etc/passwd'
                archive.addfile(member)
            else:
                member.size = len(content)
                archive.addfile(member, io.BytesIO(content))
            for asset_name, asset_content in assets:
                asset = tarfile.TarInfo(f'assets/{asset_name}')
                asset.size = len(asset_content)
                archive.addfile(asset, io.BytesIO(asset_content))
        return path

    def test_archive_cannot_escape_release_directory_or_create_links(self):
        for name, kind in [('../outside', None), ('/absolute', None), ('index.html', tarfile.SYMTYPE)]:
            with self.subTest(name=name, kind=kind):
                target = self.site / 'bad-release'
                with self.assertRaises(ValueError):
                    deploy.extract_frontend(self.archive(name, kind=kind), target)
                self.assertFalse(target.exists())
                self.assertEqual((self.live / 'index.html').read_text(), 'old frontend')

    def test_checksum_and_incoming_directory_are_required(self):
        source = self.archive()
        checksum = hashlib.sha256(source.read_bytes()).hexdigest()
        self.assertEqual(deploy.artifact_path(source, checksum), source)
        with self.assertRaises(ValueError):
            deploy.artifact_path(source, '0' * 64)
        outside = self.root / 'outside.jar'
        outside.write_bytes(b'x')
        with self.assertRaises(ValueError):
            deploy.artifact_path(outside, hashlib.sha256(b'x').hexdigest())

    def test_frontend_switch_keeps_previous_content_and_uses_relative_link(self):
        old_asset = self.live / 'assets/AppChatPage-OLD00000.js'
        old_asset.parent.mkdir()
        old_asset.write_bytes(b'old chat module')
        archive = self.archive(assets=[('AppChatPage-NEW00000.js', b'new chat module')])
        with patch.object(deploy, 'frontend_health'), patch.object(deploy, 'wait_healthy', side_effect=lambda fn, _: fn()):
            deploy.deploy_frontend(archive, '0123456789ab-1')
        self.assertTrue(self.live.is_symlink())
        self.assertFalse(Path(os.readlink(self.live)).is_absolute())
        self.assertEqual((self.live / 'index.html').read_text(), 'new frontend')
        self.assertEqual((self.site / 'releases/legacy-0123456789ab-1/index.html').read_text(), 'old frontend')
        retained = self.site / 'static-archive/assets'
        self.assertEqual((retained / old_asset.name).read_bytes(), b'old chat module')
        self.assertEqual((retained / 'AppChatPage-NEW00000.js').read_bytes(), b'new chat module')

    def test_frontend_asset_conflict_keeps_current_release(self):
        asset = self.live / 'assets/AppChatPage-OLD00000.js'
        asset.parent.mkdir()
        asset.write_bytes(b'old chat module')
        archive = self.archive(assets=[(asset.name, b'different module with the same name')])
        with self.assertRaisesRegex(ValueError, '前端资源文件名与已发布版本冲突'):
            deploy.deploy_frontend(archive, '0123456789ab-6')
        self.assertEqual((self.live / 'index.html').read_text(), 'old frontend')
        self.assertEqual((self.site / 'static-archive/assets' / asset.name).read_bytes(), b'old chat module')

    def test_frontend_failed_health_check_restores_previous_content(self):
        with patch.object(deploy, 'frontend_health', side_effect=[RuntimeError('bad response'), None]), \
                patch.object(deploy, 'wait_healthy', side_effect=lambda fn, _: fn()):
            with self.assertRaisesRegex(RuntimeError, '已恢复'):
                deploy.deploy_frontend(self.archive(), '0123456789ab-2')
        self.assertEqual((self.live / 'index.html').read_text(), 'old frontend')

    def backend(self, health_results):
        incoming = self.incoming / 'backend.jar'
        incoming.write_bytes(b'new backend')
        return incoming, patch.multiple(deploy, private_properties=Mock(return_value={'key': 'new value'}),
                                       schema_check=Mock(return_value=[]), command=Mock(),
                                       wait_healthy=Mock(side_effect=health_results))

    def test_backend_success_keeps_rollback_jar_and_private_config_permissions(self):
        incoming, mocks = self.backend([None])
        with mocks:
            deploy.deploy_backend(incoming, '0123456789ab-3', False)
        self.assertEqual(self.jar.read_bytes(), b'new backend')
        self.assertEqual(self.config.read_text(), 'key=new value\n')
        self.assertEqual(self.config.stat().st_mode & 0o777, 0o600)
        self.assertEqual((self.root / 'backend-releases/0123456789ab-3/previous.jar').read_bytes(), b'old backend')

    def test_backend_failure_restores_jar_and_configuration(self):
        incoming, mocks = self.backend([RuntimeError('unhealthy'), None])
        with mocks:
            with self.assertRaisesRegex(RuntimeError, '已恢复原 JAR'):
                deploy.deploy_backend(incoming, '0123456789ab-4', False)
        self.assertEqual(self.jar.read_bytes(), b'old backend')
        self.assertEqual(self.config.read_text(), 'old.secret=value\n')
        self.assertEqual(self.config.stat().st_mode & 0o777, 0o600)

    def test_missing_schema_blocks_switching_the_running_backend(self):
        incoming, mocks = self.backend([None])
        with mocks, patch.object(deploy, 'schema_check', return_value=['chat_history.renderData']):
            with self.assertRaisesRegex(RuntimeError, 'MIGRATE_SCHEMA'):
                deploy.deploy_backend(incoming, '0123456789ab-5', False)
        self.assertEqual(self.jar.read_bytes(), b'old backend')
        self.assertEqual(self.config.read_text(), 'old.secret=value\n')

    def test_schema_check_is_read_only_and_migration_adds_only_missing_columns(self):
        existing = {f'{table}.{column}' for table, column in deploy.COLUMNS} - {'chat_history.renderData'}
        existing.update(f'{table}.appId' for table in deploy.TABLES)
        query = Mock(return_value='\n'.join(existing))
        context = Mock()
        context.__enter__ = Mock(return_value=query)
        context.__exit__ = Mock(return_value=False)
        with patch.object(deploy, 'mysql_session', return_value=context):
            self.assertEqual(deploy.schema_check({'mysql.database': 'ai_code'}), ['chat_history.renderData'])
            self.assertEqual(query.call_count, 1)
            self.assertEqual(deploy.schema_check({'mysql.database': 'ai_code'}, True), [])
        alterations = [call.args[0] for call in query.call_args_list if call.args[0].startswith('ALTER')]
        self.assertEqual(len(alterations), 1)
        self.assertIn('ADD COLUMN `renderData`', alterations[0])

    def test_thesis_tables_are_reported_and_created_only_when_missing(self):
        existing = {f'{table}.{column}' for table, column in deploy.COLUMNS if table not in deploy.TABLES}
        query = Mock(return_value='\n'.join(existing))
        context = Mock()
        context.__enter__ = Mock(return_value=query)
        context.__exit__ = Mock(return_value=False)
        with patch.object(deploy, 'mysql_session', return_value=context):
            self.assertEqual(deploy.schema_check({'mysql.database': 'ai_code'}),
                             ['app_thesis.referenceSnapshot', *deploy.TABLES])
            self.assertEqual(query.call_count, 1)
            self.assertEqual(deploy.schema_check({'mysql.database': 'ai_code'}, True), [])
        creations = [call.args[0] for call in query.call_args_list if call.args[0].startswith('CREATE')]
        self.assertEqual(len(creations), len(deploy.TABLES))
        self.assertTrue(all('IF NOT EXISTS' in sql for sql in creations))
        statements = [call.args[0] for call in query.call_args_list]
        add_snapshot = next(sql for sql in statements if sql.startswith('ALTER TABLE'))
        self.assertIn('ADD COLUMN `referenceSnapshot`', add_snapshot)
        thesis_table = next(sql for sql in creations if 'app_thesis (' in sql)
        self.assertLess(statements.index(thesis_table), statements.index(add_snapshot))


if __name__ == '__main__':
    unittest.main()
