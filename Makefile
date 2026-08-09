.PHONY: check

check:
	cd infra && terraform fmt -check -recursive \
	  && terraform init -backend=false -input=false >/dev/null \
	  && terraform validate
