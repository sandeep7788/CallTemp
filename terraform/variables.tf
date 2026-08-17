variable "region" {

  description = "AWS region for all resources"

  default     = "ap-south-1"

}



variable "project_name" {

  description = "Short name used for all AWS resource naming (avoid dots)"

  default     = "makecall"

}



variable "domain_name" {

  description = "Public domain name for the project (used in Nginx config, SSL cert, and tags)"

  default     = "makecall.in"

}



variable "instance_type" {

  description = "EC2 instance type (ARM64 — must match ami_id architecture)"

  default     = "t4g.micro"

}



variable "ami_id" {

  description = "Amazon Linux 2023 ARM64 AMI ID for the target region"

}



variable "key_name" {

  description = "Name of the EC2 Key Pair for SSH access"

}



variable "jar_name" {

  description = "Name of the JAR file uploaded to S3 and downloaded by EC2"

  default     = "app.jar"

}