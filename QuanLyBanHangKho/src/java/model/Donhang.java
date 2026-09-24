package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Donhang {
    private Long id;
    private String madonhang;
    private Khachhang khachhang;
    private Kho kho;
    private Nguoidung nguoitao;
    private Nguoidung nguoiduyet;
    private LocalDateTime thoigiantao = LocalDateTime.now();
    private LocalDateTime thoigianduyet;
    private BigDecimal tongtien = BigDecimal.ZERO;
    private BigDecimal giamgia = BigDecimal.ZERO;
    private BigDecimal thanhtien = BigDecimal.ZERO;
    private boolean canpheduyetgia = false;
    private boolean canpheduyetcongno = false;
    private String lydoduyet;
    private String trangthai;
    private String diachigiao;
    private String ghichu;
    public Donhang() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMadonhang() { return madonhang; }
    public void setMadonhang(String madonhang) { this.madonhang = madonhang; }
    public Khachhang getKhachhang() { return khachhang; }
    public void setKhachhang(Khachhang khachhang) { this.khachhang = khachhang; }
    public Kho getKho() { return kho; }
    public void setKho(Kho kho) { this.kho = kho; }
    public Nguoidung getNguoitao() { return nguoitao; }
    public void setNguoitao(Nguoidung nguoitao) { this.nguoitao = nguoitao; }
    public Nguoidung getNguoiduyet() { return nguoiduyet; }
    public void setNguoiduyet(Nguoidung nguoiduyet) { this.nguoiduyet = nguoiduyet; }
    public LocalDateTime getThoigiantao() { return thoigiantao; }
    public void setThoigiantao(LocalDateTime thoigiantao) { this.thoigiantao = thoigiantao; }
    public LocalDateTime getThoigianduyet() { return thoigianduyet; }
    public void setThoigianduyet(LocalDateTime thoigianduyet) { this.thoigianduyet = thoigianduyet; }
    public BigDecimal getTongtien() { return tongtien; }
    public void setTongtien(BigDecimal tongtien) { this.tongtien = tongtien; }
    public BigDecimal getGiamgia() { return giamgia; }
    public void setGiamgia(BigDecimal giamgia) { this.giamgia = giamgia; }
    public BigDecimal getThanhtien() { return thanhtien; }
    public void setThanhtien(BigDecimal thanhtien) { this.thanhtien = thanhtien; }
    public boolean isCanpheduyetgia() { return canpheduyetgia; }
    public void setCanpheduyetgia(boolean canpheduyetgia) { this.canpheduyetgia = canpheduyetgia; }
    public boolean isCanpheduyetcongno() { return canpheduyetcongno; }
    public void setCanpheduyetcongno(boolean canpheduyetcongno) { this.canpheduyetcongno = canpheduyetcongno; }
    public String getLydoduyet() { return lydoduyet; }
    public void setLydoduyet(String lydoduyet) { this.lydoduyet = lydoduyet; }
    public String getTrangthai() { return trangthai; }
    public void setTrangthai(String trangthai) { this.trangthai = trangthai; }
    public String getDiachigiao() { return diachigiao; }
    public void setDiachigiao(String diachigiao) { this.diachigiao = diachigiao; }
    public String getGhichu() { return ghichu; }
    public void setGhichu(String ghichu) { this.ghichu = ghichu; }
}
