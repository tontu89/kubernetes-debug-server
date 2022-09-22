function FindProxyForURL(url, host) {
    if (dnsDomainIs(host, ".kasikornbank.com") || isInNet(dnsResolve(host), "172.0.0.0", "255.0.0.0")) {
      return "PROXY localhost:8898";
    } else {
      return "DIRECT";
    }
  }
