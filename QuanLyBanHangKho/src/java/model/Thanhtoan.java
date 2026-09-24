package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Thanhtoan {
    private Long id;
    private String maphieuthu;
    private Hoadon hoadon;
    private Khachhang khachhang;
    private BigDecimal sotien;
    private String phuongthuc;
    private LocalDateTime thoigian = LocalDateTime.now();
    private String thamchieu;
    private String ghichu;
    public Thanhtoan() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMaphieuthu() { return maphieuthu; }
    public void setMaphieuthu(String maphieuthu) { this.maphieuthu = maphieuthu; }
    public Hoadon getHoadon() { return hoadon; }
    public void setHoadon(Hoadon hoadon) { this.hoadon = hoadon; }
    public Khachhang getKhachhang() { return khachhang; }
    public void setKhachhang(Khachhang khachhang) { this.khachhang = khachhang; }
    public BigDecimal getSotien() { return sotien; }
    public void setSotien(BigDecimal sotien) { this.sotien = sotien; }
    public String getPhuongthuc() { return phuongthuc; }
    public void setPhuongthuc(String phuongthuc) { this.phuongthuc = phuongthuc; }
    public LocalDateTime getThoigian() { return thoigian; }
    public void setThoigian(LocalDateTime thoigian) { this.thoigian = thoigian; }
    public String getThamchieu() { return thamchieu; }
    public void setThamchieu(String thamchieu) { this.thamchieu = thamchieu; }
    public String getGhichu() { return ghichu; }
    public void setGhichu(String ghichu) { this.ghichu = ghichu; }
}
