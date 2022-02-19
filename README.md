# Library for debugging on Kubernetes for Spring Boot Application
Currently, we have many tool to help debugging application in Kubernetes like Telepresence, VS Code Bridge... 
However because of some reasons, we are not allow to install the Telepresence/VS Code agent in the pod, so
for debugging, the only choice is to go with JVM remote debug. While JVM remote debugging does not support for 
hot reload, every change must be deployed. Hence, it's quite inconvenience. 

My solution is creating a library that open port in Kubernetes Pod. Now, all request to the Pod will be forward to 
local server through Socket. 