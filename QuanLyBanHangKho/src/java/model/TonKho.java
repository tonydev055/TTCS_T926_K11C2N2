package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class TonKho {

    private Long id;
    private Kho kho;
    private SanPham sanpham;
    private int soluongton = 0;
    private int soluonggiu = 0;

    public TonKho() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Kho getKho() {
        return kho;
    }

    public void setKho(Kho kho) {
        this.kho = kho;
    }

    public SanPham getSanpham() {
        return sanpham;
    }

    public void setSanpham(SanPham sanpham) {
        this.sanpham = sanpham;
    }

    public int getSoluongton() {
        return soluongton;
    }

    public void setSoluongton(int soluongton) {
        this.soluongton = soluongton;
    }

    public int getSoluonggiu() {
        return soluonggiu;
    }

    public void setSoluonggiu(int soluonggiu) {
        this.soluonggiu = soluonggiu;
    }
}
