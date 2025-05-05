function FindProxyForURL(url, host) {
    // Exclude specific URL from proxy
    if (url === "https://bone.bidv.com.vn/dashboard") {
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
