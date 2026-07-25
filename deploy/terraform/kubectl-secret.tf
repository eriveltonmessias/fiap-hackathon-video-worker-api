resource "kubectl_manifest" "secret" {
  yaml_body = templatefile("${path.module}/../kubernetes/secret.yaml", local.template_vars)
}
