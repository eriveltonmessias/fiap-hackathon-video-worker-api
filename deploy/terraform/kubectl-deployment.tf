resource "kubectl_manifest" "deployment" {
  yaml_body = templatefile("${path.module}/../kubernetes/deployment.yaml", local.template_vars)

  depends_on = [
    kubectl_manifest.configmap,
    kubectl_manifest.secret,
  ]
}
