function FindProxyForURL(url, host) {
    if (shExpMatch(host, "*kasi*.com")) {
        return "PROXY localhost:8898";
    } else if (shExpMatch(host, "*fi*.dev")) {
        return "PROXY 192.168.4.205:8887";
    } else {
      return "DIRECT";
    }
  }
