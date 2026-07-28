resource "kubectl_manifest" "hpa" {
  yaml_body = templatefile("${path.module}/../kubernetes/hpa.yaml", local.template_vars)

  depends_on = [
    kubectl_manifest.deployment,
  ]
}
