function FindProxyForURL(url, host) {
    if (dnsDomainIs(host, ".kasikornbank.com") || isInNet(dnsResolve(host), "172.30.64.69", "255.255.255.255")) {
      return "PROXY localhost:8898";
    } else {
      return "DIRECT";
    }
  }
