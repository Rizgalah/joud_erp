package com.joud.dashboard.bean;

import com.joud.core.security.filter.AuthFilter;
import com.joud.core.security.session.UserSession;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.primefaces.model.charts.ChartData;
import org.primefaces.model.charts.bar.BarChartDataSet;
import org.primefaces.model.charts.bar.BarChartModel;
import org.primefaces.model.charts.bar.BarChartOptions;
import org.primefaces.model.charts.donut.DonutChartDataSet;
import org.primefaces.model.charts.donut.DonutChartModel;
import org.primefaces.model.charts.optionconfig.legend.Legend;
import org.primefaces.model.charts.optionconfig.legend.LegendLabel;
import org.primefaces.model.charts.axes.cartesian.linear.CartesianLinearAxes;
import org.primefaces.model.charts.axes.cartesian.CartesianScales;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * DashboardChartHelper — بيانات الرسوم البيانية
 * يبني PrimeFaces ChartModel لكل رسم
 */
@Named("chartHelper")
@ViewScoped
public class DashboardChartHelper implements Serializable {

    private BarChartModel salesChartModel;
    private DonutChartModel stockChartModel;
    private BarChartModel profitChartModel;

    private Long agentId;

    @PersistenceContext
    private EntityManager em;

    @PostConstruct
    public void init() {
        UserSession u = getCurrentUser();
        if (u != null)
            agentId = u.getAgentId();
        buildSalesChart();
        buildStockChart();
        buildProfitChart();
    }

    // ════════════════════════════════════════════════
    // 1. مخطط المبيعات والمشتريات (Bar Chart)
    // ════════════════════════════════════════════════

    private void buildSalesChart() {
        salesChartModel = new BarChartModel();
        ChartData data = new ChartData();

        List<String> labels = new ArrayList<>();
        List<Number> salesVals = new ArrayList<>();
        List<Number> purchVals = new ArrayList<>();

        Locale arLocale = Locale.of("ar");
        LocalDate now = LocalDate.now();

        for (int i = 5; i >= 0; i--) {
            LocalDate d = now.minusMonths(i);
            int y = d.getYear(), m = d.getMonthValue();

            labels.add(d.format(DateTimeFormatter.ofPattern("MMM yy", arLocale)));

            salesVals.add(querySum("sale", y, m));
            purchVals.add(querySum("purchase", y, m));
        }

        // Dataset المبيعات
        BarChartDataSet salesDS = new BarChartDataSet();
        salesDS.setLabel("المبيعات");
        salesDS.setData(salesVals);
        salesDS.setBackgroundColor(Collections.nCopies(6, "rgba(15,110,86,0.75)"));
        salesDS.setBorderColor(Collections.nCopies(6, "#0F6E56"));
        salesDS.setBorderWidth(Collections.nCopies(6, 1));

        // Dataset المشتريات
        BarChartDataSet purchDS = new BarChartDataSet();
        purchDS.setLabel("المشتريات");
        purchDS.setData(purchVals);
        purchDS.setBackgroundColor(Collections.nCopies(6, "rgba(27,58,107,0.65)"));
        purchDS.setBorderColor(Collections.nCopies(6, "#1B3A6B"));
        purchDS.setBorderWidth(Collections.nCopies(6, 1));

        data.addChartDataSet(salesDS);
        data.addChartDataSet(purchDS);
        data.setLabels(labels);
        salesChartModel.setData(data);

        // خيارات
        BarChartOptions opts = new BarChartOptions();
        opts.setMaintainAspectRatio(false);
        opts.setResponsive(true);

        CartesianScales scales = new CartesianScales();
        CartesianLinearAxes yAxis = new CartesianLinearAxes();
        yAxis.setBeginAtZero(true);
        scales.addYAxesData(yAxis);
        opts.setScales(scales);

        Legend legend = new Legend();
        legend.setDisplay(true);
        legend.setPosition("bottom");
        LegendLabel lbl = new LegendLabel();
        lbl.setFontFamily("Arial");
        legend.setLabels(lbl);
        opts.setLegend(legend);

        salesChartModel.setOptions(opts);
        salesChartModel.setExtender("chartExtender");
    }

    // ════════════════════════════════════════════════
    // 2. مخطط المخزون حسب الفئة (Donut Chart)
    // ════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private void buildStockChart() {
        stockChartModel = new DonutChartModel();
        ChartData data = new ChartData();

        List<String> labels = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        List<String> colors = List.of(
                "#0F6E56", "#1B3A6B", "#2E75B6", "#e67e22",
                "#8e44ad", "#e74c3c", "#009688", "#f39c12");

        try {
            List<Object[]> cats = em.createNativeQuery(
                    "SELECT NVL(c.categ_c_name,'أخرى'), " +
                            "NVL(SUM(p.current_quantity*p.prod_f_avg_cost),0) val " +
                            "FROM products p LEFT JOIN categories c ON p.categ_i_id=c.categ_i_id " +
                            "WHERE p.prod_c_status='A' AND p.prod_c_track_stock='Y' " +
                            "GROUP BY c.categ_c_name ORDER BY val DESC")
                    .setMaxResults(7).getResultList();

            for (Object[] row : cats) {
                labels.add(row[0] != null ? row[0].toString() : "أخرى");
                values.add(row[1] != null ? new BigDecimal(row[1].toString()).doubleValue() : 0.0);
            }
        } catch (Exception ignored) {
        }

        if (values.isEmpty()) {
            labels.add("لا توجد بيانات");
            values.add(1);
        }

        DonutChartDataSet ds = new DonutChartDataSet();
        ds.setData(values);
        ds.setBackgroundColor(colors.subList(0, Math.min(values.size(), colors.size())));
        ds.setBorderWidth(Collections.nCopies(values.size(), 2));
        ds.setBorderColor(Collections.nCopies(values.size(), "#ffffff"));

        data.addChartDataSet(ds);
        data.setLabels(labels);
        stockChartModel.setData(data);
    }

    // ════════════════════════════════════════════════
    // 3. مخطط الأرباح الشهرية
    // ════════════════════════════════════════════════

    private void buildProfitChart() {
        profitChartModel = new BarChartModel();
        ChartData data = new ChartData();

        List<String> labels = new ArrayList<>();
        List<Number> profits = new ArrayList<>();

        Locale arLocale = Locale.of("ar");
        LocalDate now = LocalDate.now();

        for (int i = 5; i >= 0; i--) {
            LocalDate d = now.minusMonths(i);
            labels.add(d.format(DateTimeFormatter.ofPattern("MMM yy", arLocale)));
            double profit = querySum("sale", d.getYear(), d.getMonthValue()).doubleValue()
                    - querySum("purchase", d.getYear(), d.getMonthValue()).doubleValue();
            profits.add(profit);
        }

        BarChartDataSet ds = new BarChartDataSet();
        ds.setLabel("صافي الربح");
        ds.setData(profits);
        ds.setBackgroundColor(profits.stream()
                .map(v -> v.doubleValue() >= 0
                        ? "rgba(15,110,86,0.75)"
                        : "rgba(231,76,60,0.75)")
                .toList());

        data.addChartDataSet(ds);
        data.setLabels(labels);
        profitChartModel.setData(data);

        BarChartOptions opts = new BarChartOptions();
        opts.setMaintainAspectRatio(false);
        opts.setResponsive(true);
        CartesianScales scales = new CartesianScales();
        CartesianLinearAxes yAxis = new CartesianLinearAxes();
        yAxis.setBeginAtZero(true);
        scales.addYAxesData(yAxis);
        opts.setScales(scales);
        profitChartModel.setOptions(opts);
    }

    // ─── Helpers ───────────────────────────────────

    private BigDecimal querySum(String type, int year, int month) {
        try {
            Object r = em.createNativeQuery(
                    "SELECT NVL(SUM(total),0) FROM invoices " +
                            "WHERE AGENT_I_ID=:a AND invoice_c_type=:t AND status='posted' " +
                            "AND EXTRACT(YEAR FROM invoice_d_date)=:y " +
                            "AND EXTRACT(MONTH FROM invoice_d_date)=:m")
                    .setParameter("a", agentId).setParameter("t", type)
                    .setParameter("y", year).setParameter("m", month)
                    .getSingleResult();
            return r != null ? new BigDecimal(r.toString()) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private UserSession getCurrentUser() {
        return (UserSession) FacesContext.getCurrentInstance()
                .getExternalContext().getRequestMap().get(AuthFilter.SESSION_ATTR);
    }

    // Getters
    public BarChartModel getSalesChartModel() {
        if (salesChartModel == null) {
            buildSalesChart();
        }
        return salesChartModel;
    }

    public DonutChartModel getStockChartModel() {
        if (stockChartModel == null) {
            buildStockChart();
        }
        return stockChartModel;
    }

    public BarChartModel getProfitChartModel() {
        if (profitChartModel == null) {
            buildProfitChart();
        }
        return profitChartModel;
    }
}
