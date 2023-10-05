function FindProxyForURL(url, host) {
    if (shExpMatch(host, "*kasi*.com") || shExpMatch(host, "*fis*.dev")) {
        return "PROXY localhost:8898";
    } else {
      return "DIRECT";
    }
  }
