package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Kho {
    private Long id;
    private String makho;
    private String tenkho;
    private String diachi;
    private boolean trangthai = true;
    public Kho() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMakho() { return makho; }
    public void setMakho(String makho) { this.makho = makho; }
    public String getTenkho() { return tenkho; }
    public void setTenkho(String tenkho) { this.tenkho = tenkho; }
    public String getDiachi() { return diachi; }
    public void setDiachi(String diachi) { this.diachi = diachi; }
    public boolean isTrangthai() { return trangthai; }
    public void setTrangthai(boolean trangthai) { this.trangthai = trangthai; }
}
