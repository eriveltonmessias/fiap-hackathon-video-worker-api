variable "kubeconfig_path" {
  type    = string
  default = "~/.kube/config"
}

variable "kubeconfig_context" {
  type    = string
  default = ""
}

variable "apps_namespace" {
  type    = string
  default = "fiap-x-apps"
}

variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "name_prefix" {
  type    = string
  default = "fiapx"
}

variable "image" {
  type = string
}

variable "replicas" {
  type    = number
  default = 1
}

variable "s3_access_key" {
  type      = string
  sensitive = true
}

variable "s3_secret_key" {
  type      = string
  sensitive = true
}
