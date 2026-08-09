# AMI는 SSM 최신값이라 갱신 시 인스턴스가 교체되므로 ignore_changes로 고정한다
data "aws_ssm_parameter" "ecs_ami" {
  name = "/aws/service/ecs/optimized-ami/amazon-linux-2023/arm64/recommended/image_id"
}

resource "aws_instance" "gateway" {
  ami                    = data.aws_ssm_parameter.ecs_ami.value
  instance_type          = "t4g.small"
  subnet_id              = aws_subnet.public["${var.region}a"].id
  vpc_security_group_ids = [aws_security_group.instance.id]
  iam_instance_profile   = aws_iam_instance_profile.instance.name

  root_block_device {
    volume_type = "gp3"
    volume_size = 30
    encrypted   = true
  }

  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  user_data = <<-EOT
    #!/bin/bash
    echo "ECS_CLUSTER=${local.cluster_name}" >> /etc/ecs/ecs.config
    mkdir -p ${local.duckdb_ext_dir} /caddy-data
  EOT

  tags = {
    Name = local.app_name
  }

  lifecycle {
    ignore_changes = [ami]
  }
}

resource "aws_eip" "gateway" {
  domain = "vpc"

  tags = {
    Name = local.app_name
  }
}

resource "aws_eip_association" "gateway" {
  instance_id   = aws_instance.gateway.id
  allocation_id = aws_eip.gateway.id
}
