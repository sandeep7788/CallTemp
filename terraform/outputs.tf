######################################################################
# outputs.tf — Terraform outputs for makecall.in infrastructure
# Region: ap-south-1 (Mumbai, India)
######################################################################

output "aws_region" {
  description = "AWS region where all resources are deployed"
  value       = var.region
}

output "availability_zone" {
  description = "Availability zone where EC2 instance and subnet are deployed"
  value       = "${var.region}a"
}

output "instance_id" {
  description = "EC2 Instance ID"
  value       = aws_instance.app.id
}

output "instance_type" {
  description = "EC2 instance type"
  value       = aws_instance.app.instance_type
}

output "ami_id" {
  description = "AMI used for the EC2 instance"
  value       = aws_instance.app.ami
}

output "public_ip" {
  description = "EC2 public IPv4 address"
  value       = aws_instance.app.public_ip
}

output "public_dns" {
  description = "EC2 public DNS hostname"
  value       = aws_instance.app.public_dns
}

output "ssh_command" {
  description = "SSH command to connect to the EC2 instance"
  value       = "ssh -i ~/.ssh/${var.key_name}.pem ec2-user@${aws_instance.app.public_ip}"
}

output "application_url" {
  description = "Application URL via HTTP"
  value       = "http://${var.domain_name}"
}

output "application_url_https" {
  description = "Application URL via HTTPS"
  value       = "https://${var.domain_name}"
}

output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.main.id
}

output "s3_bucket_name" {
  description = "S3 bucket name used for JAR deployment"
  value       = aws_s3_bucket.bucket.bucket
}

output "iam_role_arn" {
  description = "IAM role ARN attached to the EC2 instance"
  value       = aws_iam_role.role.arn
}

output "bootstrap_log_command" {
  description = "Command to watch EC2 bootstrap progress"
  value       = "ssh -i ~/.ssh/${var.key_name}.pem ec2-user@${aws_instance.app.public_ip} 'sudo tail -f /var/log/user-data.log'"
}

output "app_logs_command" {
  description = "Command to tail Spring Boot application logs"
  value       = "ssh -i ~/.ssh/${var.key_name}.pem ec2-user@${aws_instance.app.public_ip} 'sudo journalctl -u myapp.service -f'"
}

output "deploy_jar_command" {
  description = "Command to upload the Spring Boot JAR to S3"
  value       = "aws s3 cp target/app.jar s3://${aws_s3_bucket.bucket.bucket}/app.jar"
}

output "redeploy_command" {
  description = "Full redeploy: upload JAR to S3 then restart the service on EC2"
  value       = "aws s3 cp target/app.jar s3://${aws_s3_bucket.bucket.bucket}/app.jar && ssh -i ~/.ssh/${var.key_name}.pem ec2-user@${aws_instance.app.public_ip} 'sudo aws s3 cp s3://${aws_s3_bucket.bucket.bucket}/app.jar /opt/app/app.jar && sudo systemctl restart myapp.service'"
}