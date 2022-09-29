function FindProxyForURL(url, host) {
    if (dnsDomainIs(host, ".kasikornbank.com") || isInNet(dnsResolve(host), "172.30.64.69", "255.255.255.255") ||
         isInNet(dnsResolve(host), "172.30.141.125", "255.255.255.255") ||
         isInNet(dnsResolve(host), "172.30.72.39", "255.255.255.255") ||
         isInNet(dnsResolve(host), "172.30.87.57", "255.255.255.255") ||
         isInNet(dnsResolve(host), "172.30.166.21", "255.255.255.255") ||
         isInNet(dnsResolve(host), "172.30.83.49", "255.255.255.255") ||
         isInNet(dnsResolve(host), "172.30.83.95", "255.255.255.255")) {
      return "PROXY localhost:8898";
    } else {
      return "DIRECT";
    }
  }
