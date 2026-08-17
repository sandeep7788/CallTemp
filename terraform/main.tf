provider "aws" {

  region = var.region

}

resource "aws_vpc" "main" {

  cidr_block           = "10.0.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags = {
    Name = "${var.project_name}-vpc"
  }

}

resource "aws_internet_gateway" "igw" {

  vpc_id = aws_vpc.main.id
  tags = {
    Name = "${var.project_name}-igw"
  }

}

resource "aws_subnet" "public_subnet" {

  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.1.0/24"
  map_public_ip_on_launch = true
  availability_zone       = "${var.region}a"
  tags = {
    Name = "${var.project_name}-public-subnet"
  }

}

resource "aws_route_table" "public_rt" {

  vpc_id = aws_vpc.main.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.igw.id
  }
  tags = {
    Name = "${var.project_name}-public-rt"
  }

}

resource "aws_route_table_association" "public_assoc" {

  subnet_id      = aws_subnet.public_subnet.id
  route_table_id = aws_route_table.public_rt.id
}

resource "aws_s3_bucket" "bucket" {

  bucket = "${var.project_name}-jar-bucket"
  tags = {
    Name = "${var.project_name}-jar-bucket"
  }

}

locals {

  jar_path = "${path.module}/../target/app.jar"

}

resource "aws_s3_object" "jar" {

  bucket = aws_s3_bucket.bucket.id
  key    = "app.jar"
  source = local.jar_path
  etag   = filemd5(local.jar_path)

}

resource "aws_iam_role" "role" {

  name = "${var.project_name}-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect    = "Allow",
      Principal = { Service = "ec2.amazonaws.com" },
      Action    = "sts:AssumeRole"
    }]
  })

}

resource "aws_iam_policy" "s3_read_jar" {

  name        = "${var.project_name}-s3-read-jar"
  description = "Allow read access to the project's jar in S3"
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect   = "Allow",
        Action   = ["s3:GetObject", "s3:GetObjectVersion"],
        Resource = "arn:aws:s3:::${var.project_name}-jar-bucket/*"
      },
      {
        Effect   = "Allow",
        Action   = ["s3:ListBucket"],
        Resource = "arn:aws:s3:::${var.project_name}-jar-bucket"
      }
    ]
  })

}

resource "aws_iam_role_policy_attachment" "s3_read_attach" {

  role       = aws_iam_role.role.name
  policy_arn = aws_iam_policy.s3_read_jar.arn
}

resource "aws_iam_instance_profile" "profile" {

  name = "${var.project_name}-profile"
  role = aws_iam_role.role.name
}

resource "aws_security_group" "sg" {

  name   = "${var.project_name}-sg"
  vpc_id = aws_vpc.main.id

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-sg"
  }

}

resource "aws_instance" "app" {

  ami                         = var.ami_id
  instance_type               = var.instance_type
  key_name                    = var.key_name
  subnet_id                   = aws_subnet.public_subnet.id
  vpc_security_group_ids      = [aws_security_group.sg.id]
  user_data_replace_on_change = true
  iam_instance_profile        = aws_iam_instance_profile.profile.name
  associate_public_ip_address = true

  depends_on = [aws_s3_object.jar]

  user_data_base64 = base64encode(templatefile("${path.module}/user_data.sh", {
    domain_name = var.domain_name
    s3_bucket   = "${var.project_name}-jar-bucket"
    jar_name    = var.jar_name
  }))

  tags = {
    Name   = var.project_name
    Domain = var.domain_name
  }

}

/*resource "aws_eip" "app" {

  domain = "vpc"
  tags = {
    Name = "${var.project_name}-eip"
  }

}

resource "aws_eip_association" "app" {

  instance_id   = aws_instance.app.id
  allocation_id = aws_eip.app.id
}*/