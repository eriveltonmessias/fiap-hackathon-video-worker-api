locals {
  template_vars = {
    apps_namespace        = data.terraform_remote_state.data_platform.outputs.apps_namespace
    image                 = "${data.terraform_remote_state.infra.outputs.ecr_repository_urls["video-worker-api"]}:${var.image_tag}"
    replicas              = var.replicas
    s3_access_key         = jsonencode(var.s3_access_key)
    s3_secret_key         = jsonencode(var.s3_secret_key)
    s3_endpoint           = "https://s3.${var.aws_region}.amazonaws.com"
    s3_input_bucket_name  = data.terraform_remote_state.data_platform.outputs.s3_input_bucket
    s3_output_bucket_name = data.terraform_remote_state.data_platform.outputs.s3_output_bucket
  }
}
