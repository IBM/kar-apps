This chart deploys the Reefer application on Kubernetes.

You must override the values of `ingress.subdomain` and `ingress.tls.secretname`
to provide the real values for your cluster.  On an IKS cluster, you can
find the values to use by doing:
```shell
ibmcloud ks cluster get --cluster CLUSTER_NAME | grep Ingress
```
