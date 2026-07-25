resource "kubectl_manifest" "service" {
  yaml_body = templatefile("${path.module}/../kubernetes/service.yaml", local.template_vars)
}
