function FindProxyForURL(url, host) {
    if (shExpMatch(host, "*kasi*.com")) {
        return "PROXY localhost:8898";
    } else {
      return "DIRECT";
    }
  }
