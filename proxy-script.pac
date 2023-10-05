function FindProxyForURL(url, host) {
    if (shExpMatch(host, "*kasi*.com")) {
        return "PROXY localhost:8898";
    } else if (shExpMatch(host, "*fis*.dev")) {
        return "PROXY localhost:8887";
    } else {
      return "DIRECT";
    }
  }
