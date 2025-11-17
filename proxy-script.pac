function FindProxyForURL(url, host) {
    // Exclude specific URL from proxy
    if (shExpMatch(host, "*bone.*bid*.com.*")) {
         //return "PROXY 192.168.4.205:8887";
        return "DIRECT";
    }

    if (shExpMatch(host, "*kasi*.com")) {
        return "PROXY localhost:8898";
    } else if (shExpMatch(host, "*fi*.dev")) {
        return "PROXY 192.168.4.205:8887";
    } else if (shExpMatch(host, "*bid*.com.*")) {
        return "PROXY 192.168.4.205:8887";
    } else {
        return "DIRECT";
    }
}



