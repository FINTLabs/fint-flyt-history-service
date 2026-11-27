#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE_DIR="$ROOT/kustomize/templates"
DEFAULT_TEMPLATE="$TEMPLATE_DIR/overlay.yaml.tpl"

extra_user_orgs_for_namespace() {
  local namespace="$1"
  case "$namespace" in
    afk-no|bfk-no|ofk-no)
      printf 'viken.no,frid-iks.no'
      ;;
    *)
      printf ''
      ;;
  esac
}

app_instance_suffix() {
  local namespace="$1"
  case "$namespace" in
    bym-oslo-kommune-no)
      printf '%s' "$namespace"
      ;;
    *)
      printf '%s' "${namespace//-/_}"
      ;;
  esac
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

  AUTHORIZATION_SSO_PATCHES=""
  if [[ "$namespace" == "ra-no" ]]; then
    AUTHORIZATION_SSO_PATCHES=$'\n'"$(cat <<'EOF'
      - op: replace
        path: "/spec/env/1/valueFrom/secretKeyRef/key"
        value: "fint.flyt.authorization.sso.client-id"
      - op: replace
        path: "/spec/env/2/valueFrom/secretKeyRef/key"
        value: "fint.flyt.authorization.sso.client-secret"
EOF
)"
  fi
  export AUTHORIZATION_SSO_PATCHES

  LEGACY_ERROR_LISTENER_PATCHES=""
  if [[ "$namespace" == "ra-no" ]]; then
    LEGACY_ERROR_LISTENER_PATCHES=$'\n'"$(cat <<'EOF'
      - op: add
        path: "/spec/env/-"
        value:
         name: "NOVARI_FLYT_HISTORY_SERVICE_KAFKA_LEGACY_ERROR_TOPIC_LISTENERS_ENABLED"
         value: "false"
EOF
)"
  fi
  export LEGACY_ERROR_LISTENER_PATCHES

  export NAMESPACE="$namespace"
  export ORG_ID="${namespace//-/.}"
  export INSTANCE_DATABASE_SCHEMA="${ORG_ID//./_}_fint_flyt_instance_service_db"
  export APP_INSTANCE_LABEL="fint-flyt-history-service_$(app_instance_suffix "$namespace")"
  export KAFKA_TOPIC="${namespace}.flyt.*"
  export INGRESS_BASE_PATH="${path_prefix}/api/intern/instance-flow-tracking"
  export SERVLET_CONTEXT_PATH="$path_prefix"
  export STARTUP_PATH="${path_prefix}/actuator/health/liveness"
  export READINESS_PATH="${path_prefix}/actuator/health/readiness"
  export LIVENESS_PATH="${path_prefix}/actuator/health/liveness"
  export METRICS_PATH="${path_prefix}/actuator/prometheus"
  extra_orgs="$(extra_user_orgs_for_namespace "$namespace")"
  ADDITIONAL_ORG_IDS_PATCH=""
  if [[ -n "$extra_orgs" ]]; then
    ADDITIONAL_ORG_IDS_PATCH=$'\n      - op: add\n        path: "/spec/env/-"\n        value:\n'
    ADDITIONAL_ORG_IDS_PATCH+=$'          name: "novari.flyt.security.tenant.additional-org-ids"\n'
    ADDITIONAL_ORG_IDS_PATCH+=$'          value: "'"${extra_orgs}"$'"'
  fi
  export ADDITIONAL_ORG_IDS_PATCH
  export NOVARI_KAFKA_TOPIC_ORGID="$namespace"

  template="$(choose_template "$env_path")"
  target_dir="$ROOT/kustomize/overlays/$dir"
  mkdir -p "$target_dir"

  tmp="$(mktemp "$target_dir/.kustomization.yaml.XXXXXX")"
  envsubst '$AUTHORIZATION_SSO_PATCHES $NAMESPACE $APP_INSTANCE_LABEL $ORG_ID $KAFKA_TOPIC $INGRESS_BASE_PATH $SERVLET_CONTEXT_PATH $STARTUP_PATH $READINESS_PATH $LIVENESS_PATH $METRICS_PATH $ADDITIONAL_ORG_IDS_PATCH $NOVARI_KAFKA_TOPIC_ORGID $INSTANCE_DATABASE_SCHEMA $LEGACY_ERROR_LISTENER_PATCHES' \
    < "$template" > "$tmp"
  mv "$tmp" "$target_dir/kustomization.yaml"
done < <(
  {
    find "$ROOT/kustomize/overlays" -name kustomization.yaml -print
    printf '%s\n' \
      "$ROOT/kustomize/overlays/ra-no/beta/kustomization.yaml" \
      "$ROOT/kustomize/overlays/ra-no/api/kustomization.yaml"
  } | sort -u
)
