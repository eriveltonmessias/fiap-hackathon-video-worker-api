provider "kubectl" {
  config_path    = pathexpand(var.kubeconfig_path)
  config_context = var.kubeconfig_context == "" ? null : var.kubeconfig_context
}
