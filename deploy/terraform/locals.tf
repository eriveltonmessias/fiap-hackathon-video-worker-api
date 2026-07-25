locals {
  template_vars = {
    apps_namespace        = var.apps_namespace
    image                 = var.image
    replicas              = var.replicas
    s3_access_key         = var.s3_access_key
    s3_secret_key         = var.s3_secret_key
    s3_endpoint           = "https://s3.${var.aws_region}.amazonaws.com"
    s3_input_bucket_name  = "${var.name_prefix}-videos-input"
    s3_output_bucket_name = "${var.name_prefix}-videos-output"
  }
}
