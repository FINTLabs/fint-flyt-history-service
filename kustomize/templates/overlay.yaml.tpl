apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
namespace: $NAMESPACE

resources:
  - ../../../base

labels:
  - pairs:
      app.kubernetes.io/instance: $APP_INSTANCE_LABEL
      fintlabs.no/org-id: $ORG_ID

patches:
  - patch: |-
      - op: replace
        path: "/spec/kafka/acls/0/topic"
        value: "$KAFKA_TOPIC"
      - op: replace
        path: "/spec/orgId"
        value: "$ORG_ID"
      - op: replace
        path: "/spec/url/basePath"
        value: "$URL_BASE_PATH"
      - op: replace
        path: "/spec/ingress/basePath"
        value: "$INGRESS_BASE_PATH"
      - op: replace
        path: "/spec/env/1/value"
        value: |
$AUTHORIZED_ORG_ROLE_PAIRS
      - op: add
        path: "/spec/env/-"
        value:
         name: "novari.kafka.topic.orgId"
         value: "$FINT_KAFKA_TOPIC_ORG_ID"
      - op: replace
        path: "/spec/probes/readiness/path"
        value: "$READINESS_PATH"
      - op: replace
        path: "/spec/observability/metrics/path"
        value: "$METRICS_PATH"
    target:
      kind: Application
      name: fint-flyt-history-service
