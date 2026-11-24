#!/usr/bin/python3
"""生产端发布入口；由 root 安装，Jenkins 通过受限 sudo 规则调用。"""

import argparse
import contextlib
import fcntl
import hashlib
import json
import os
import pathlib
import pwd
import re
import shutil
import subprocess
import tarfile
import tempfile
import time
import urllib.request

APP = pathlib.Path('/project')
SITE = pathlib.Path('/baiye/1panel/www/sites/ai-code')
INCOMING = APP / 'ci-incoming'
BACKEND_RELEASES = APP / 'ci-releases/backend'
FRONTEND_RELEASES = SITE / 'releases'
VERSIONED_ASSET = re.compile(r'.+-[A-Za-z0-9_-]{8}(?:\.[A-Za-z0-9]+)+')
LIVE_JAR = APP / 'ai-code-0.0.1-SNAPSHOT.jar'
ACTIVE_CONFIG = APP / 'config/application-prod.properties'
PRIVATE_CONFIG = pathlib.Path('/etc/academiccraft-ci/backend.json')
MYSQL_CONTAINER = '1Panel-mysql-Jm2J'
SERVICE = 'ai-code.service'
HOST = 'academic.craft.wangkairui.top'

COLUMNS = {
    ('app', 'generateBackend'): "TINYINT NOT NULL DEFAULT 0 COMMENT '是否同步生成后端'",
    ('app', 'nodePort'): "INT NULL COMMENT '全栈应用宿主端口'",
    ('app', 'containerName'): "VARCHAR(64) NULL COMMENT '应用容器名'",
    ('app', 'containerStatus'): "VARCHAR(16) NULL COMMENT '应用容器状态'",
    ('chat_history', 'renderData'): "MEDIUMTEXT NULL COMMENT 'AG-UI 展示快照'",
    ('app', 'generateThesis'): "TINYINT NOT NULL DEFAULT 0 COMMENT '自动生成论文初稿'",
    ('app', 'thesisTitle'): "VARCHAR(200) NULL COMMENT '论文题目'",
}

TABLES = {
    'app_thesis': """CREATE TABLE IF NOT EXISTS app_thesis (
        appId BIGINT NOT NULL PRIMARY KEY, title VARCHAR(200) NOT NULL,
        status VARCHAR(24) NOT NULL, stage VARCHAR(100) NOT NULL,
        revision VARCHAR(36) NOT NULL, sourceVersion VARCHAR(64) NULL,
        sourceContext MEDIUMTEXT NULL, errorMessage VARCHAR(1000) NULL,
        createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) COLLATE = utf8mb4_unicode_ci""",
    'app_thesis_chapter': """CREATE TABLE IF NOT EXISTS app_thesis_chapter (
        id BIGINT NOT NULL PRIMARY KEY, appId BIGINT NOT NULL, chapterNo INT NOT NULL,
        title VARCHAR(100) NOT NULL, content MEDIUMTEXT NULL, status VARCHAR(24) NOT NULL,
        sourceVersion VARCHAR(64) NULL, userEdited TINYINT NOT NULL DEFAULT 0,
        errorMessage VARCHAR(1000) NULL, UNIQUE KEY uk_app_chapter (appId, chapterNo)
    ) COLLATE = utf8mb4_unicode_ci""",
}


def command(args, *, input_text=None):
    result = subprocess.run(args, input=input_text, text=True, capture_output=True, timeout=150)
    if result.returncode:
        # 不把配置输入或数据库认证信息输出到 Jenkins 控制台。
        raise RuntimeError(f'命令执行失败：{args[0]}，退出码 {result.returncode}')
    return result.stdout.strip()


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def artifact_path(path, checksum):
    path = pathlib.Path(path).resolve(strict=True)
    if not path.is_relative_to(INCOMING.resolve()) or not path.is_file():
        raise ValueError('发布产物必须位于 /project/ci-incoming')
    if not re.fullmatch(r'[0-9a-f]{64}', checksum) or digest(path) != checksum:
        raise ValueError('发布产物校验和不匹配')
    return path


def extract_frontend(archive, destination):
    with tarfile.open(archive, 'r:gz') as source:
        members = source.getmembers()
        for member in members:
            relative = pathlib.PurePosixPath(member.name)
            if relative.is_absolute() or '..' in relative.parts or not (member.isfile() or member.isdir()):
                raise ValueError('前端压缩包包含不允许的路径或文件类型')
        destination.mkdir(parents=True, exist_ok=False)
        for member in members:
            target = destination / member.name
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True, mode=0o755)
            else:
                target.parent.mkdir(parents=True, exist_ok=True, mode=0o755)
                with source.extractfile(member) as src, target.open('wb') as dst:
                    shutil.copyfileobj(src, dst)
                target.chmod(0o644)
    if not (destination / 'index.html').is_file():
        raise ValueError('前端产物缺少 index.html')


def replace_link(live, target):
    temporary = live.with_name(f'.{live.name}-deploy-{os.getpid()}')
    temporary.symlink_to(os.path.relpath(target, live.parent), target_is_directory=True)
    os.replace(temporary, live)


def retain_frontend_assets(release):
    assets = release / 'assets'
    if assets.is_symlink():
        return
    for asset in assets.rglob('*'):
        if asset.is_symlink() or not VERSIONED_ASSET.fullmatch(asset.name) or not asset.is_file():
            continue
        relative = asset.relative_to(assets)
        retained = SITE / 'static-archive' / 'assets' / relative
        retained.parent.mkdir(parents=True, exist_ok=True)
        try:
            os.link(asset, retained)
        except FileExistsError:
            if not asset.samefile(retained) and digest(asset) != digest(retained):
                raise ValueError(f'前端资源文件名与已发布版本冲突：{relative}')


def http_body(url, host=None):
    headers = {'Cache-Control': 'no-cache'}
    if host:
        headers['Host'] = host
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(urllib.request.Request(url, headers=headers), timeout=5) as response:
        if response.status != 200:
            raise RuntimeError('健康检查没有返回 HTTP 200')
        return response.read()


def wait_healthy(check, seconds):
    deadline = time.monotonic() + seconds
    while True:
        try:
            check()
            return
        except Exception:
            if time.monotonic() >= deadline:
                raise RuntimeError('发布后的健康检查超时') from None
            time.sleep(2)


def frontend_health(index):
    actual = hashlib.sha256(http_body('http://127.0.0.1/index.html', HOST)).hexdigest()
    if actual != digest(index):
        raise RuntimeError('站点尚未返回本次发布的前端入口')


def deploy_frontend(archive, release):
    target = FRONTEND_RELEASES / release
    extract_frontend(archive, target)
    live = SITE / 'index'
    # 切换前保留两版资源，旧标签页和回滚期间的页面仍可加载。
    retain_frontend_assets(live)
    retain_frontend_assets(target)
    previous = None
    if live.is_symlink():
        previous = live.resolve(strict=True)
    elif live.exists():
        previous = FRONTEND_RELEASES / f'legacy-{release}'
        os.replace(live, previous)
    try:
        # 相对链接在宿主机及 OpenResty 的 /www bind mount 内都能解析。
        replace_link(live, target)
        wait_healthy(lambda: frontend_health(target / 'index.html'), 30)
    except Exception:
        if previous:
            replace_link(live, previous)
            wait_healthy(lambda: frontend_health(previous / 'index.html'), 30)
        elif live.is_symlink():
            live.unlink()
        raise RuntimeError('前端发布失败，已恢复上一个入口') from None


def properties_text(properties):
    def escape(value):
        return str(value).replace('\\', '\\\\').replace('\r', '\\r').replace('\n', '\\n')
    return ''.join(f'{key}={escape(value)}\n' for key, value in sorted(properties.items()))


def private_properties():
    properties = json.loads(PRIVATE_CONFIG.read_text())
    required = ['mysql.host', 'mysql.port', 'mysql.database', 'mysql.username', 'mysql.password',
                'redis.host', 'redis.port', 'redis.password', 'deepseek.api-key', 'deepseek.model-name',
                'minio.endpoint', 'minio.accessKey', 'minio.secretKey',
                'aliyun.dashscope.api-key', 'aliyun.dashscope.model-id']
    missing = [key for key in required if not str(properties.get(key, '')).strip()]
    if missing:
        raise ValueError('缺少生产配置项：' + ', '.join(missing))
    return properties


@contextlib.contextmanager
def mysql_session(properties):
    def quote(value):
        return '"' + str(value).replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n') + '"'
    config = '[client]\n' + ''.join(f'{key}={quote(properties[setting])}\n' for key, setting in {
        'host': 'mysql.host', 'port': 'mysql.port', 'database': 'mysql.database',
        'user': 'mysql.username', 'password': 'mysql.password',
    }.items())
    # 认证内容只经 stdin 进入临时文件，不进入进程参数和构建日志。
    temporary = command(['docker', 'exec', '-i', MYSQL_CONTAINER, 'sh', '-c',
                         'umask 077; f=$(mktemp /tmp/academiccraft-ci.XXXXXX); cat > "$f"; printf "%s" "$f"'],
                        input_text=config)
    if not re.fullmatch(r'/tmp/academiccraft-ci\.[A-Za-z0-9]+', temporary):
        raise RuntimeError('MySQL 临时配置路径无效')
    try:
        def query(sql):
            return command(['docker', 'exec', MYSQL_CONTAINER, 'mysql',
                            f'--defaults-extra-file={temporary}', '--batch', '--skip-column-names', '--execute', sql])
        yield query
    finally:
        command(['docker', 'exec', MYSQL_CONTAINER, 'rm', '-f', temporary])


def schema_check(properties, migrate=False):
    database = properties['mysql.database']
    if not re.fullmatch(r'[A-Za-z0-9_]+', database):
        raise ValueError('数据库名称无效')
    with mysql_session(properties) as query:
        rows = query("SELECT CONCAT(TABLE_NAME,'.',COLUMN_NAME) FROM information_schema.COLUMNS "
                     f"WHERE TABLE_SCHEMA='{database}' AND TABLE_NAME IN ('app','chat_history','app_thesis','app_thesis_chapter')")
        existing = set(rows.splitlines())
        missing = [(table, column) for table, column in COLUMNS if f'{table}.{column}' not in existing]
        existing_tables = {item.split('.', 1)[0] for item in existing}
        missing_tables = [table for table in TABLES if table not in existing_tables]
        if migrate:
            for table in missing_tables:
                query(TABLES[table])
            for table, column in missing:
                query(f'ALTER TABLE `{table}` ADD COLUMN `{column}` {COLUMNS[(table, column)]}')
            print(json.dumps({'addedColumns': ['.'.join(item) for item in missing], 'addedTables': missing_tables}), flush=True)
            return []
        return ['.'.join(item) for item in missing] + missing_tables


def backend_health():
    result = json.loads(http_body('http://127.0.0.1:8123/api/health/'))
    if result.get('code') != 0 or result.get('data') != 'ok':
        raise RuntimeError('后端业务健康检查失败')


def atomic_copy(source, destination):
    temporary = destination.with_name(f'.{destination.name}-deploy-{os.getpid()}')
    shutil.copy2(source, temporary)
    if destination == ACTIVE_CONFIG:
        config_permissions(temporary)
    os.replace(temporary, destination)


def config_permissions(path):
    account = pwd.getpwnam('baiye')
    path.chmod(0o600)
    os.chown(path, account.pw_uid, account.pw_gid)


def activate_config(properties):
    with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=ACTIVE_CONFIG.parent,
                                     prefix='.application-prod-', delete=False) as stream:
        stream.write(properties_text(properties))
        temporary = pathlib.Path(stream.name)
    config_permissions(temporary)
    os.replace(temporary, ACTIVE_CONFIG)


def deploy_backend(artifact, release, migrate):
    properties = private_properties()
    missing = schema_check(properties, migrate)
    if missing:
        raise RuntimeError('缺少数据库列：' + ', '.join(missing) + '；首次发布需勾选 MIGRATE_SCHEMA')
    target = BACKEND_RELEASES / release
    target.mkdir(parents=True, exist_ok=False)
    staged = target / 'app.jar'
    shutil.copy2(artifact, staged)
    staged.chmod(0o644)
    if digest(staged) != digest(artifact):
        raise ValueError('后端产物复制后校验失败')
    if not LIVE_JAR.is_file():
        raise ValueError('原后端 JAR 不存在，拒绝覆盖未知部署')
    previous = target / 'previous.jar'
    shutil.copy2(LIVE_JAR, previous)
    had_config = ACTIVE_CONFIG.exists()
    if had_config:
        shutil.copy2(ACTIVE_CONFIG, target / 'previous.properties')
    try:
        activate_config(properties)
        atomic_copy(staged, LIVE_JAR)
        command(['systemctl', 'restart', SERVICE])
        wait_healthy(backend_health, 120)
    except Exception:
        atomic_copy(previous, LIVE_JAR)
        if had_config:
            atomic_copy(target / 'previous.properties', ACTIVE_CONFIG)
        elif ACTIVE_CONFIG.exists():
            ACTIVE_CONFIG.unlink()
        command(['systemctl', 'restart', SERVICE])
        wait_healthy(backend_health, 120)
        raise RuntimeError('后端发布失败，已恢复原 JAR 和生产配置；新增列保留兼容旧版') from None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--component', choices=['frontend', 'backend'], required=True)
    parser.add_argument('--release', required=True)
    parser.add_argument('--artifact')
    parser.add_argument('--sha256')
    parser.add_argument('--check-only', action='store_true')
    parser.add_argument('--migrate-schema', action='store_true')
    args = parser.parse_args()
    if os.geteuid() != 0:
        parser.error('需要通过受限 sudo 发布入口执行')
    if not re.fullmatch(r'[0-9a-f]{12}-[1-9][0-9]*', args.release):
        parser.error('发布版本号无效')
    with open('/run/lock/academiccraft-deploy.lock', 'a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        if args.check_only:
            if args.component == 'backend':
                missing = schema_check(private_properties())
                print(json.dumps({'preflight': 'ok', 'pendingColumns': missing}), flush=True)
            else:
                if not (SITE / 'index/index.html').is_file():
                    raise ValueError('生产站点入口不存在')
                frontend_health(SITE / 'index/index.html')
                print(json.dumps({'preflight': 'ok', 'site': str(SITE)}), flush=True)
            return
        if not args.artifact or not args.sha256:
            parser.error('发布需要产物路径和 SHA-256')
        artifact = artifact_path(args.artifact, args.sha256)
        if args.component == 'frontend':
            deploy_frontend(artifact, args.release)
        else:
            deploy_backend(artifact, args.release, args.migrate_schema)
        print(json.dumps({'deployed': args.component, 'release': args.release, 'health': 'ok'}), flush=True)


if __name__ == '__main__':
    main()
