package polaris.utils.proxy;

import com.google.gson.annotations.Expose;

public class ProxyData {
    @Expose private String name;
    @Expose private String ip;
    @Expose private int port;
    @Expose private String login;
    @Expose private String password;
    @Expose private ProxyType type;

    private transient int ping = -1; // -1 = failed, -2 = pinging, >0 = ms

    public enum ProxyType {
        HTTP, SOCKS4, SOCKS5
    }

    public ProxyData(String name, String ip, int port, String login, String password, ProxyType type) {
        this.name = name;
        this.ip = ip;
        this.port = port;
        this.login = login != null ? login : "";
        this.password = password != null ? password : "";
        this.type = type != null ? type : ProxyType.SOCKS5;
    }

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public ProxyType getType() { return type; }
    public void setType(ProxyType type) { this.type = type; }

    public int getPing() { return ping; }
    public void setPing(int ping) { this.ping = ping; }

    public String getDisplayAddress() {
        return ip + ":" + port;
    }
}