package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Tonkho {
    private Long id;
    private Kho kho;
    private Sanpham sanpham;
    private int soluongton = 0;
    private int soluonggiu = 0;
    public Tonkho() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Kho getKho() { return kho; }
    public void setKho(Kho kho) { this.kho = kho; }
    public Sanpham getSanpham() { return sanpham; }
    public void setSanpham(Sanpham sanpham) { this.sanpham = sanpham; }
    public int getSoluongton() { return soluongton; }
    public void setSoluongton(int soluongton) { this.soluongton = soluongton; }
    public int getSoluonggiu() { return soluonggiu; }
    public void setSoluonggiu(int soluonggiu) { this.soluonggiu = soluonggiu; }
}
