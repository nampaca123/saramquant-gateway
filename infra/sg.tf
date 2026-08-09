# SSH 포트는 열지 않는다 — 셸 접근은 SSM Session Manager 경유
resource "aws_security_group" "instance" {
  name        = "${local.app_name}-instance"
  description = "Gateway host: public HTTP/HTTPS in, all out."
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${local.app_name}-instance"
  }
}

resource "aws_vpc_security_group_ingress_rule" "http" {
  security_group_id = aws_security_group.instance.id
  description       = "Caddy HTTP (ACME challenge + redirect to HTTPS)."
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "https" {
  security_group_id = aws_security_group.instance.id
  description       = "Caddy HTTPS."
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.instance.id
  description       = "Allow all outbound traffic."
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
