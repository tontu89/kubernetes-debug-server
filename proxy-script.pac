function FindProxyForURL(url, host) {
    if (dnsDomainIs(host, ".kasikornbank.com")) {
      return "PROXY localhost:8898";
    } else {
      return "DIRECT";
    }
  }