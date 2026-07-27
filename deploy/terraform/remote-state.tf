data "terraform_remote_state" "infra" {
  backend = "s3"
  config = {
    bucket       = var.state_bucket
    key          = "infra/terraform.tfstate"
    region       = var.aws_region
    encrypt      = true
    use_lockfile = true
  }
}

data "terraform_remote_state" "data_platform" {
  backend = "s3"
  config = {
    bucket       = var.state_bucket
    key          = "data-platform/terraform.tfstate"
    region       = var.aws_region
    encrypt      = true
    use_lockfile = true
  }
}
