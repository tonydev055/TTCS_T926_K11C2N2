package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Phieuxuat {
    private Long id;
    private String maphieuxuat;
    private Donhang donhang;
    private Kho kho;
    private LocalDateTime thoigian = LocalDateTime.now();
    private String trangthai;
    private String ghichu;
    public Phieuxuat() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMaphieuxuat() { return maphieuxuat; }
    public void setMaphieuxuat(String maphieuxuat) { this.maphieuxuat = maphieuxuat; }
    public Donhang getDonhang() { return donhang; }
    public void setDonhang(Donhang donhang) { this.donhang = donhang; }
    public Kho getKho() { return kho; }
    public void setKho(Kho kho) { this.kho = kho; }
    public LocalDateTime getThoigian() { return thoigian; }
    public void setThoigian(LocalDateTime thoigian) { this.thoigian = thoigian; }
    public String getTrangthai() { return trangthai; }
    public void setTrangthai(String trangthai) { this.trangthai = trangthai; }
    public String getGhichu() { return ghichu; }
    public void setGhichu(String ghichu) { this.ghichu = ghichu; }
}
