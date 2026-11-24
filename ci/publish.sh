#!/usr/bin/env bash
set -euo pipefail
set +x

component="${1:?需要指定 frontend 或 backend}"
action="${2:?需要指定 check 或 deploy}"
[[ "$component" == frontend || "$component" == backend ]]
[[ "$action" == check || "$action" == deploy ]]
[[ "$RELEASE" =~ ^[0-9a-f]{12}-[1-9][0-9]*$ ]]
[[ "$DEPLOY_USER" == baiye ]]
[[ "$DEPLOY_HOST" == 192.168.0.178 && "$DEPLOY_PORT" == 22 ]]

ssh_opts=(-i "$DEPLOY_KEY" -o BatchMode=yes -o StrictHostKeyChecking=yes
    -o "UserKnownHostsFile=$JENKINS_HOME/.ssh/known_hosts" -o ConnectTimeout=15)
host="$DEPLOY_USER@$DEPLOY_HOST"
command=(sudo -n /usr/local/sbin/academiccraft-deploy --component "$component" --release "$RELEASE")

if [[ "$action" == check ]]; then
    ssh "${ssh_opts[@]}" -p "$DEPLOY_PORT" "$host" "${command[*]} --check-only"
    exit 0
fi

extension=jar
[[ "$component" == frontend ]] && extension=tgz
artifact="artifacts/$component-$RELEASE.$extension"
checksum="$(sha256sum "$artifact" | cut -d ' ' -f 1)"
[[ "$checksum" =~ ^[0-9a-f]{64}$ ]]
remote="/project/ci-incoming/$component-$RELEASE.$extension"
ssh "${ssh_opts[@]}" -p "$DEPLOY_PORT" "$host" 'mkdir -p /project/ci-incoming'
scp "${ssh_opts[@]}" -P "$DEPLOY_PORT" "$artifact" "$host:$remote"
command+=(--artifact "$remote" --sha256 "$checksum")
[[ "$component" == backend && "${MIGRATE_SCHEMA:-false}" == true ]] && command+=(--migrate-schema)
ssh "${ssh_opts[@]}" -p "$DEPLOY_PORT" "$host" "${command[*]}"
