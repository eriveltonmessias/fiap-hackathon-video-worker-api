terraform {
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.30.0, < 7.0.0"
    }

    kubectl = {
      source  = "gavinbunney/kubectl"
      version = "~> 1.19"
    }
  }

  backend "s3" {
    key          = "apps/video-worker-api/terraform.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }
}
