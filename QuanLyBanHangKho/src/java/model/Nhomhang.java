package model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Nhomhang {
    private Long id;
    private String manhom;
    private String tennhom;
    private Nhomhang nhomcha;
    private boolean trangthai = true;
    public Nhomhang() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getManhom() { return manhom; }
    public void setManhom(String manhom) { this.manhom = manhom; }
    public String getTennhom() { return tennhom; }
    public void setTennhom(String tennhom) { this.tennhom = tennhom; }
    public Nhomhang getNhomcha() { return nhomcha; }
    public void setNhomcha(Nhomhang nhomcha) { this.nhomcha = nhomcha; }
    public boolean isTrangthai() { return trangthai; }
    public void setTrangthai(boolean trangthai) { this.trangthai = trangthai; }
}
