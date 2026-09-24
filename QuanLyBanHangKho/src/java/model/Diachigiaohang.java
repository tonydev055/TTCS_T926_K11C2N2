package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Diachigiaohang {
    private Long id;
    private Khachhang khachhang;
    private String tennguoinhan;
    private String sodienthoai;
    private String diachi;
    private boolean macdinh = false;
    public Diachigiaohang() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Khachhang getKhachhang() { return khachhang; }
    public void setKhachhang(Khachhang khachhang) { this.khachhang = khachhang; }
    public String getTennguoinhan() { return tennguoinhan; }
    public void setTennguoinhan(String tennguoinhan) { this.tennguoinhan = tennguoinhan; }
    public String getSodienthoai() { return sodienthoai; }
    public void setSodienthoai(String sodienthoai) { this.sodienthoai = sodienthoai; }
    public String getDiachi() { return diachi; }
    public void setDiachi(String diachi) { this.diachi = diachi; }
    public boolean isMacdinh() { return macdinh; }
    public void setMacdinh(boolean macdinh) { this.macdinh = macdinh; }
}
