resource "kubectl_manifest" "configmap" {
  yaml_body = templatefile("${path.module}/../kubernetes/configmap.yaml", local.template_vars)
}
