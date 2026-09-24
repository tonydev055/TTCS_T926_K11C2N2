package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Chuyenkho {
    private Long id;
    private String machuyen;
    private Kho khonguon;
    private Kho khodich;
    private String trangthai;
    private LocalDateTime thoigian = LocalDateTime.now();
    private String ghichu;
    public Chuyenkho() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMachuyen() { return machuyen; }
    public void setMachuyen(String machuyen) { this.machuyen = machuyen; }
    public Kho getKhonguon() { return khonguon; }
    public void setKhonguon(Kho khonguon) { this.khonguon = khonguon; }
    public Kho getKhodich() { return khodich; }
    public void setKhodich(Kho khodich) { this.khodich = khodich; }
    public String getTrangthai() { return trangthai; }
    public void setTrangthai(String trangthai) { this.trangthai = trangthai; }
    public LocalDateTime getThoigian() { return thoigian; }
    public void setThoigian(LocalDateTime thoigian) { this.thoigian = thoigian; }
    public String getGhichu() { return ghichu; }
    public void setGhichu(String ghichu) { this.ghichu = ghichu; }
}
