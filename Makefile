export NAMESPACE=test
export OPERATOR_VERSION=0.1.2

release:
	sbt -mem 2048 compile test 'release with-defaults'

run-docker:
	docker run \
	  -e APP_CONFIG_PATH=/opt/docker/resources/application.conf \
	  -v ~/.kube:/home/demiourgos728/.kube \
	  -e K8S_SPECS_DIR=/opt/docker/resources \
	  alexeyn/kerberos-operator2:0.1.0-SNAPSHOT

docker-local-build:
	sbt docker:publishLocal

create-krb-server:
	kubectl create -f examples/my-krb-server-1.yaml -n $(NAMESPACE)
delete-krb-server:
	kubectl delete -f examples/my-krb-server-1.yaml -n $(NAMESPACE)
create-krb-principals:
	kubectl create -f examples/my-principals-1.yaml -n $(NAMESPACE)
delete-krb-principals:
	kubectl delete -f examples/my-principals-1.yaml -n $(NAMESPACE)

install-rbac:
	dhall-to-yaml < manifest/rbac.dhall | kubectl create -n ${NAMESPACE} -f -
uninstall-rbac:
	dhall-to-yaml < manifest/rbac.dhall | kubectl delete -n ${NAMESPACE} -f -

install: install-rbac	
	dhall-to-yaml < manifest/kube-deployment.dhall | kubectl create -n ${NAMESPACE} -f -	
uninstall: uninstall-rbac
	dhall-to-yaml < manifest/kube-deployment.dhall | kubectl delete -n ${NAMESPACE} -f -	

to-dhall:
	yaml-to-dhall '(https://raw.githubusercontent.com/dhall-lang/dhall-kubernetes/a4126b7f8f0c0935e4d86f0f596176c41efbe6fe/types.dhall).ConfigMap' --file ./manifest/openshift-deployment.yaml \
	  | dhall rewrite-with-schemas --schemas 'https://raw.githubusercontent.com/dhall-lang/dhall-kubernetes/a4126b7f8f0c0935e4d86f0f596176c41efbe6fe/schemas.dhall'
