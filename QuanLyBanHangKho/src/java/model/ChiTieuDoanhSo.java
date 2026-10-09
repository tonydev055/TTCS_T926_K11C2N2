package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class ChiTieuDoanhSo {

    private Long id;
    private NguoiDung nguoidung;
    private LocalDate tungay;
    private LocalDate denngay;
    private BigDecimal chitieutien;
    private boolean trangthai = true;

    public ChiTieuDoanhSo() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public NguoiDung getNguoidung() {
        return nguoidung;
    }

    public void setNguoidung(NguoiDung nguoidung) {
        this.nguoidung = nguoidung;
    }

    public LocalDate getTungay() {
        return tungay;
    }

    public void setTungay(LocalDate tungay) {
        this.tungay = tungay;
    }

    public LocalDate getDenngay() {
        return denngay;
    }

    public void setDenngay(LocalDate denngay) {
        this.denngay = denngay;
    }

    public BigDecimal getChitieutien() {
        return chitieutien;
    }

    public void setChitieutien(BigDecimal chitieutien) {
        this.chitieutien = chitieutien;
    }

    public boolean isTrangthai() {
        return trangthai;
    }

    public void setTrangthai(boolean trangthai) {
        this.trangthai = trangthai;
    }
}
