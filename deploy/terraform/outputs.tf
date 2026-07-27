output "cluster_name" {
  value = data.terraform_remote_state.infra.outputs.cluster_name
}
