#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE_DIR="$ROOT/kustomize/templates"
DEFAULT_TEMPLATE="$TEMPLATE_DIR/overlay.yaml.tpl"

USER_ROLE_URL="USER"
DEVELOPER_ROLE_URL="DEVELOPER"

extra_user_orgs_for_namespace() {
  local namespace="$1"
  case "$namespace" in
    afk-no|bfk-no|ofk-no)
      printf 'viken.no frid-iks.no'
      ;;
    *)
      printf ''
      ;;
  esac
}

render_authorized_role_pairs() {
  local org_id="$1"
  shift

  local entries=("\"${org_id}\":[\"${USER_ROLE_URL}\"]")
  for extra_org in "$@"; do
    entries+=("\"${extra_org}\":[\"${USER_ROLE_URL}\"]")
  done
  entries+=("\"vigo.no\":[\"${DEVELOPER_ROLE_URL}\",\"${USER_ROLE_URL}\"]")
  entries+=("\"novari.no\":[\"${DEVELOPER_ROLE_URL}\",\"${USER_ROLE_URL}\"]")

  local total="${#entries[@]}"
  printf '          {\n'
  for idx in "${!entries[@]}"; do
    local comma=","
    if [[ "$idx" == "$((total - 1))" ]]; then
      comma=""
    fi
    printf '            %s%s\n' "${entries[$idx]}" "$comma"
  done
  printf '          }\n'
}

choose_template() {
  local env_path="$1"
  if [[ -z "$env_path" ]]; then
    printf '%s' "$DEFAULT_TEMPLATE"
    return
  fi

  local candidate="overlay-${env_path//\//-}.yaml.tpl"
  local candidate_path="$TEMPLATE_DIR/$candidate"

  if [[ -f "$candidate_path" ]]; then
    printf '%s' "$candidate_path"
  else
    printf '%s' "$DEFAULT_TEMPLATE"
  fi
}

while IFS= read -r file; do
  rel="${file#"$ROOT/kustomize/overlays/"}"
  dir="$(dirname "$rel")"

  namespace="${dir%%/*}"
  env_path="${dir#*/}"
  if [[ "$env_path" == "$namespace" ]]; then
    env_path=""
  fi

  path_prefix="/$namespace"
  if [[ -n "$env_path" && "$env_path" != "api" ]]; then
    path_prefix="/${env_path}/$namespace"
  fi

  declare -a additional_user_orgs=()
  extra_orgs="$(extra_user_orgs_for_namespace "$namespace")"
  if [[ -n "$extra_orgs" ]]; then
    for extra_org in $extra_orgs; do
      additional_user_orgs+=("$extra_org")
    done
  fi

  export NAMESPACE="$namespace"
  export FINT_KAFKA_TOPIC_ORG_ID="$namespace"
  export ORG_ID="${namespace//-/.}"
  export APP_INSTANCE_LABEL="fint-flyt-history-service_${namespace//-/_}"
  export KAFKA_TOPIC="${namespace}.flyt.*"
  export URL_BASE_PATH="$path_prefix"
  export INGRESS_BASE_PATH="${path_prefix}/api/intern/instance-flow-tracking"
  export READINESS_PATH="${path_prefix}/actuator/health"
  export METRICS_PATH="${path_prefix}/actuator/prometheus"
  if ((${#additional_user_orgs[@]})); then
    AUTHORIZED_ORG_ROLE_PAIRS="$(render_authorized_role_pairs "$ORG_ID" "${additional_user_orgs[@]}")"
  else
    AUTHORIZED_ORG_ROLE_PAIRS="$(render_authorized_role_pairs "$ORG_ID")"
  fi
  export AUTHORIZED_ORG_ROLE_PAIRS

  template="$(choose_template "$env_path")"
  target_dir="$ROOT/kustomize/overlays/$dir"

  tmp="$(mktemp "$target_dir/.kustomization.yaml.XXXXXX")"
  envsubst '$NAMESPACE $FINT_KAFKA_TOPIC_ORG_ID $APP_INSTANCE_LABEL $ORG_ID $KAFKA_TOPIC $URL_BASE_PATH $INGRESS_BASE_PATH $READINESS_PATH $METRICS_PATH $AUTHORIZED_ORG_ROLE_PAIRS' \
    < "$template" > "$tmp"
  mv "$tmp" "$target_dir/kustomization.yaml"
done < <(find "$ROOT/kustomize/overlays" -name kustomization.yaml -print | sort)
