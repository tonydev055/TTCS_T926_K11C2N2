package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Sanpham {
    private Long id;
    private String masanpham;
    private String tensanpham;
    private Nhomhang nhomhang;
    private String donvicoso;
    private BigDecimal giavon = BigDecimal.ZERO;
    private BigDecimal giaban = BigDecimal.ZERO;
    private String hinhanh;
    private boolean trangthai = true;
    private LocalDateTime thoigiantao = LocalDateTime.now();
    public Sanpham() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMasanpham() { return masanpham; }
    public void setMasanpham(String masanpham) { this.masanpham = masanpham; }
    public String getTensanpham() { return tensanpham; }
    public void setTensanpham(String tensanpham) { this.tensanpham = tensanpham; }
    public Nhomhang getNhomhang() { return nhomhang; }
    public void setNhomhang(Nhomhang nhomhang) { this.nhomhang = nhomhang; }
    public String getDonvicoso() { return donvicoso; }
    public void setDonvicoso(String donvicoso) { this.donvicoso = donvicoso; }
    public BigDecimal getGiavon() { return giavon; }
    public void setGiavon(BigDecimal giavon) { this.giavon = giavon; }
    public BigDecimal getGiaban() { return giaban; }
    public void setGiaban(BigDecimal giaban) { this.giaban = giaban; }
    public String getHinhanh() { return hinhanh; }
    public void setHinhanh(String hinhanh) { this.hinhanh = hinhanh; }
    public boolean isTrangthai() { return trangthai; }
    public void setTrangthai(boolean trangthai) { this.trangthai = trangthai; }
    public LocalDateTime getThoigiantao() { return thoigiantao; }
    public void setThoigiantao(LocalDateTime thoigiantao) { this.thoigiantao = thoigiantao; }
}
