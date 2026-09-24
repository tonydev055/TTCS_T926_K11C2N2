package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Giaohang {
    private Long id;
    private Donhang donhang;
    private String nguoigiao;
    private String tuyengiao;
    private String trangthai;
    private LocalDateTime thoigianbatdau;
    private LocalDateTime thoigianhoanthanh;
    private String anhchungtu;
    private String ghichu;
    public Giaohang() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Donhang getDonhang() { return donhang; }
    public void setDonhang(Donhang donhang) { this.donhang = donhang; }
    public String getNguoigiao() { return nguoigiao; }
    public void setNguoigiao(String nguoigiao) { this.nguoigiao = nguoigiao; }
    public String getTuyengiao() { return tuyengiao; }
    public void setTuyengiao(String tuyengiao) { this.tuyengiao = tuyengiao; }
    public String getTrangthai() { return trangthai; }
    public void setTrangthai(String trangthai) { this.trangthai = trangthai; }
    public LocalDateTime getThoigianbatdau() { return thoigianbatdau; }
    public void setThoigianbatdau(LocalDateTime thoigianbatdau) { this.thoigianbatdau = thoigianbatdau; }
    public LocalDateTime getThoigianhoanthanh() { return thoigianhoanthanh; }
    public void setThoigianhoanthanh(LocalDateTime thoigianhoanthanh) { this.thoigianhoanthanh = thoigianhoanthanh; }
    public String getAnhchungtu() { return anhchungtu; }
    public void setAnhchungtu(String anhchungtu) { this.anhchungtu = anhchungtu; }
    public String getGhichu() { return ghichu; }
    public void setGhichu(String ghichu) { this.ghichu = ghichu; }
}
