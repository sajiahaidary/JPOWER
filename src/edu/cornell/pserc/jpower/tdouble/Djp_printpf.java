/*
 * Copyright (C) 1996-2010 by Power System Engineering Research Center (PSERC)
 * Copyright (C) 2010 Richard Lincoln
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 *
 */

package edu.cornell.pserc.jpower.tdouble;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Map;

import cern.colt.matrix.tdcomplex.DComplexFactory1D;
import cern.colt.matrix.tdcomplex.DComplexMatrix1D;
import cern.colt.matrix.tdouble.DoubleFactory1D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tint.IntFactory1D;
import cern.colt.matrix.tint.IntMatrix1D;
import cern.colt.matrix.tint.algo.IntSorting;
import cern.jet.math.tdcomplex.DComplexFunctions;
import cern.jet.math.tdouble.DoubleFunctions;
import cern.jet.math.tint.IntFunctions;

import edu.cornell.pserc.jpower.tdouble.jpc.Djp_branch;
import edu.cornell.pserc.jpower.tdouble.jpc.Djp_bus;
import edu.cornell.pserc.jpower.tdouble.jpc.Djp_gen;
import edu.cornell.pserc.jpower.tdouble.jpc.Djp_jpc;
import edu.cornell.pserc.jpower.tdouble.util.Djp_util;

public class Djp_printpf {

	private static final Djp_util util = new Djp_util();
	private static final IntFunctions ifunc = IntFunctions.intFunctions;
	private static final DoubleFunctions dfunc = DoubleFunctions.functions;
	private static final DComplexFunctions cfunc = DComplexFunctions.functions;

	public static void jp_printpf(Djp_jpc results) {
		jp_printpf(results, System.out);
	}

	public static void jp_printpf(Djp_jpc results, OutputStream output) {
		jp_printpf(results, output, Djp_jpoption.jp_jpoption());
	}

	@SuppressWarnings("static-access")
	public static void jp_printpf(Djp_jpc results, OutputStream output, Map<String, Double> jpopt) {
		PrintWriter pw = new PrintWriter(output);

		if (jpopt.get("OUT_ALL").equals(0) || jpopt.get("OUT_RAW").equals(0))
			return;

		double baseMVA = results.baseMVA;
		Djp_bus bus = results.bus.copy();
		Djp_gen gen = results.gen.copy();
		Djp_branch branch = results.branch.copy();
		boolean success = results.success;
		double et = results.et;
		Double f = results.f;

		boolean isOPF = (f != null);	/* FALSE -> only simple PF data, TRUE -> OPF data */

		/* options */
		boolean isDC			= jpopt.get("PF_DC") == 1;	// use DC formulation?
		int OUT_ALL				= jpopt.get("OUT_ALL").intValue();
		boolean OUT_ANY			= OUT_ALL == 1;     // set to true if any pretty output is to be generated
		boolean OUT_SYS_SUM		= OUT_ALL == 1 || (OUT_ALL == -1 && jpopt.get("OUT_SYS_SUM") == 1);
		boolean OUT_AREA_SUM	= OUT_ALL == 1 || (OUT_ALL == -1 && jpopt.get("OUT_AREA_SUM") == 1);
		boolean OUT_BUS			= OUT_ALL == 1 || (OUT_ALL == -1 && jpopt.get("OUT_BUS") == 1);
		boolean OUT_BRANCH		= OUT_ALL == 1 || (OUT_ALL == -1 && jpopt.get("OUT_BRANCH") == 1);
		boolean OUT_GEN			= OUT_ALL == 1 || (OUT_ALL == -1 && jpopt.get("OUT_GEN") == 1);
		OUT_ANY					= OUT_ANY || (OUT_ALL == -1 &&
			(OUT_SYS_SUM || OUT_AREA_SUM || OUT_BUS || OUT_BRANCH || OUT_GEN));
		int OUT_ALL_LIM;
		if (OUT_ALL == -1) {
			OUT_ALL_LIM = jpopt.get("OUT_ALL_LIM").intValue();
		} else if (OUT_ALL == 1) {
			OUT_ALL_LIM = 2;
		} else {
			OUT_ALL_LIM = 0;
		}
		OUT_ANY         = OUT_ANY || OUT_ALL_LIM >= 1;

		int OUT_V_LIM;
		boolean OUT_LINE_LIM;
		boolean OUT_PG_LIM;
		boolean OUT_QG_LIM;
		if (OUT_ALL_LIM == -1) {
			OUT_V_LIM       = jpopt.get("OUT_V_LIM").intValue();
			OUT_LINE_LIM    = jpopt.get("OUT_LINE_LIM") == 1;
			OUT_PG_LIM      = jpopt.get("OUT_PG_LIM") == 1;
			OUT_QG_LIM      = jpopt.get("OUT_QG_LIM") == 1;
		} else {
			OUT_V_LIM       = OUT_ALL_LIM;
			OUT_LINE_LIM    = OUT_ALL_LIM == 1;
			OUT_PG_LIM      = OUT_ALL_LIM == 1;
			OUT_QG_LIM      = OUT_ALL_LIM == 1;
		}
		OUT_ANY			= OUT_ANY || (OUT_ALL_LIM == -1 && (OUT_V_LIM > 0 || OUT_LINE_LIM || OUT_PG_LIM || OUT_QG_LIM));
		boolean OUT_RAW	= jpopt.get("OUT_RAW") == 1;
		double ptol = 1e-6;		// tolerance for displaying shadow prices

		/* internal bus number */
		IntMatrix1D i2e = bus.bus_i.copy();
		IntMatrix1D e2i = IntFactory1D.sparse.make(i2e.aggregate(ifunc.max, ifunc.identity));
		e2i.viewSelection(i2e.toArray()).assign( Djp_util.irange(bus.size()) );

		/* sizes */
		int nb = bus.size();		// number of buses
		int nl = branch.size();		// number of branches
		int ng = gen.size();		// number of generators

		/* zero out some data to make printout consistent for DC case */
		if (isDC) {
			bus.Qd.assign(0);
			bus.Bs.assign(0);
			gen.Qg.assign(0);
			gen.Qmax.assign(0);
			gen.Qmin.assign(0);
			branch.br_r.assign(0);
			branch.br_b.assign(0);
		}

		/* parameters */
		IntMatrix1D tiesm = bus.bus_area.viewSelection(e2i.viewSelection(branch.f_bus.toArray()).toArray());
		tiesm.assign(bus.bus_area.viewSelection(e2i.viewSelection(branch.t_bus.toArray()).toArray()), IntFunctions.equals);
		tiesm.assign(IntFunctions.equals(0));
		int[] ties = tiesm.toArray();	// area inter-ties

		DComplexMatrix1D tap = DComplexFactory1D.dense.make(nl, new double[] {1, 0});	// default tap ratio = 1 for lines
		int[] xfmr = util.nonzero(branch.tap);							// indices of transformers
		tap.viewSelection(xfmr).assignReal(branch.tap.viewSelection(xfmr));	// include transformer tap ratios
		tap.assign(util.polar(tap.getRealPart(), branch.shift, false));	// add phase shifters

		IntMatrix1D ld = util.intm(bus.Pd);
		ld.assign(util.intm(bus.Qd), ifunc.or);
		int[] nzld = util.nonzero(ld);

		IntMatrix1D sorted_areas = IntSorting.quickSort.sort(bus.bus_area);
		IntMatrix1D s_areasm = sorted_areas.viewSelection(util.nonzero(util.diff(sorted_areas))).copy();
		int[] s_areas = s_areasm.toArray();		// area numbers

		IntMatrix1D shunt = util.intm(bus.Gs);
		shunt.assign(util.intm(bus.Bs), ifunc.or);
		int[] nzsh = util.nonzero(shunt);

		IntMatrix1D isload = Djp_isload.jp_isload(gen);
		IntMatrix1D notload = isload.copy();
		notload.assign(ifunc.equals(0));

		int[] allg = util.nonzero(notload);
		IntMatrix1D gs = gen.gen_status.copy();
		gs.assign(notload, ifunc.and);
		int[] ong  = util.nonzero(gs);
		gs = gen.gen_status.copy();
		gs.assign(isload, ifunc.and);
		int[] onld = util.nonzero(gs);

		DComplexMatrix1D V = util.polar(bus.Vm, bus.Va, false);
		IntMatrix1D bs = branch.br_status.copy();

		int[] out = util.nonzero( bs.assign(ifunc.equals(0)) );		// out-of-service branches
		int nout = out.length;

		DComplexMatrix1D loss = DComplexFactory1D.dense.make(nl);;
		if (!isDC) {
			DComplexMatrix1D z = DComplexFactory1D.dense.make(nl);
			z.assignReal(branch.br_r);
			z.assignImaginary(branch.br_x);
			loss.assign(V.viewSelection(e2i.viewSelection(branch.f_bus.toArray()).toArray()));
			loss.assign(tap, cfunc.div);
			loss.assign(V.viewSelection(e2i.viewSelection(branch.t_bus.toArray()).toArray()), cfunc.minus);
			loss.assign(cfunc.abs).assign(cfunc.square).assign(z, cfunc.div);
			loss.assign(cfunc.mult(baseMVA));
		}
		DComplexMatrix1D br_b = DComplexFactory1D.dense.make(nl);
		br_b.assignReal(branch.br_b).assign(cfunc.mult(baseMVA)).assign(cfunc.div(2));

		DComplexMatrix1D cfchg = DComplexFactory1D.dense.make(nl);
		cfchg.assign(V.viewSelection(e2i.viewSelection(branch.f_bus.toArray()).toArray()));
		cfchg.assign(tap, cfunc.div).assign(cfunc.abs).assign(cfunc.square).assign(br_b, cfunc.mult);
		DoubleMatrix1D fchg = cfchg.getRealPart();

		DComplexMatrix1D ctchg = DComplexFactory1D.dense.make(nl);
		ctchg.assign(V.viewSelection(e2i.viewSelection(branch.t_bus.toArray()).toArray()));
		ctchg.assign(cfunc.abs).assign(cfunc.square).assign(br_b, cfunc.mult);
		DoubleMatrix1D tchg = ctchg.getRealPart();

		loss.viewSelection(out).assign( DComplexFactory1D.dense.make(nout) );
		fchg.viewSelection(out).assign( DoubleFactory1D.dense.make(nout) );
		tchg.viewSelection(out).assign( DoubleFactory1D.dense.make(nout) );

		/* ----- print the stuff ----- */
		if (OUT_ANY) {
			/* convergence & elapsed time */
			if (success) {
				pw.printf("\nConverged in %.2f seconds", et);
			} else {
				pw.printf("\nDid not converge (%.2f seconds)\n", et);
			}

			/* objective function value */
			if (isOPF)
				pw.printf("\nObjective Function Value = %.2f $/hr", f);
		}

		if (OUT_SYS_SUM) {
			pw.printf("\n================================================================================");
			pw.printf("\n|     System Summary                                                           |");
			pw.printf("\n================================================================================");
			pw.printf("\n\nHow many?                How much?              P (MW)            Q (MVAr)");
			pw.printf("\n---------------------    -------------------  -------------  -----------------");
			pw.printf("\nBuses         %6d     Total Gen Capacity   %7.1f       %7.1f to %.1f", nb, gen.Pmax.viewSelection(allg).zSum(), gen.Qmin.viewSelection(allg).zSum(), gen.Qmax.viewSelection(allg).zSum());
			pw.printf("\nGenerators     %5d     On-line Capacity     %7.1f       %7.1f to %.1f", allg.length, gen.Pmax.viewSelection(ong).zSum(), gen.Qmin.viewSelection(ong).zSum(), gen.Qmax.viewSelection(ong).zSum());
			pw.printf("\nCommitted Gens %5d     Generation (actual)  %7.1f           %7.1f", ong.length, gen.Pg.viewSelection(ong).zSum(), gen.Qg.viewSelection(ong).zSum());
			pw.printf("\nLoads          %5d     Load                 %7.1f           %7.1f", nzld.length+onld.length, bus.Pd.viewSelection(nzld).zSum()-gen.Pg.viewSelection(onld).zSum(), bus.Qd.viewSelection(nzld).zSum()-gen.Qg.viewSelection(onld).zSum());
			pw.printf("\n  Fixed        %5d       Fixed              %7.1f           %7.1f", nzld.length, bus.Pd.viewSelection(nzld).zSum(), bus.Qd.viewSelection(nzld).zSum());
			pw.printf("\n  Dispatchable %5d       Dispatchable       %7.1f of %-7.1f%7.1f", onld.length, -gen.Pg.viewSelection(onld).zSum(), -gen.Pmin.viewSelection(onld).zSum(), -gen.Pg.viewSelection(onld).zSum());
			DoubleMatrix1D Pinj = DoubleFactory1D.dense.make(bus.Vm.viewSelection(nzsh).toArray());
			Pinj.assign(dfunc.square).assign(bus.Gs.viewSelection(nzsh), dfunc.mult);
			DoubleMatrix1D Qinj = DoubleFactory1D.dense.make(bus.Vm.viewSelection(nzsh).toArray());
			Qinj.assign(dfunc.square).assign(bus.Bs.viewSelection(nzsh), dfunc.mult);
			pw.printf("\nShunts         %5d     Shunt (inj)          %7.1f           %7.1f", nzsh.length, -Pinj.zSum(), Qinj.zSum());
			pw.printf("\nBranches       %5d     Losses (I^2 * Z)     %8.2f          %8.2f", nl, loss.getRealPart().zSum(), loss.getImaginaryPart().zSum());
			pw.printf("\nTransformers   %5d     Branch Charging (inj)     -            %7.1f", xfmr.length, fchg.zSum() + tchg.zSum() );
			DoubleMatrix1D Ptie = DoubleFactory1D.dense.make(branch.Pf.viewSelection(ties).toArray());
			Ptie.assign(branch.Pt.viewSelection(ties), dfunc.minus).assign(dfunc.abs);
			DoubleMatrix1D Qtie = DoubleFactory1D.dense.make(branch.Qf.viewSelection(ties).toArray());
			Qtie.assign(branch.Qt.viewSelection(ties), dfunc.minus).assign(dfunc.abs);
			pw.printf("\nInter-ties     %5d     Total Inter-tie Flow %7.1f           %7.1f", ties.length, Ptie.zSum() / 2, Qtie.zSum() / 2);
			pw.printf("\nAreas          %5d", s_areas.length);
			pw.printf("\n");
			pw.printf("\n                          Minimum                      Maximum");
			pw.printf("\n                 -------------------------  --------------------------------");

			double[] min, max;
			int mini, maxi;
			min = bus.Vm.getMinLocation();
			max = bus.Vm.getMaxLocation();
			mini = new Double(min[1]).intValue();
			maxi = new Double(max[1]).intValue();
			pw.printf("\nVoltage Magnitude %7.3f p.u. @ bus %-4d     %7.3f p.u. @ bus %-4d", min[0], bus.bus_i.get(mini), max[0], bus.bus_i.get(maxi));

			min = bus.Va.getMinLocation();
			max = bus.Va.getMaxLocation();
			mini = new Double(min[1]).intValue();
			maxi = new Double(max[1]).intValue();
			pw.printf("\nVoltage Angle   %8.2f deg   @ bus %-4d   %8.2f deg   @ bus %-4d", min[0], bus.bus_i.get(mini), max[0], bus.bus_i.get(maxi));

			if (!isDC) {
				min = loss.getRealPart().getMinLocation();
				max = loss.getRealPart().getMaxLocation();
				mini = new Double(min[1]).intValue();
				maxi = new Double(max[1]).intValue();
				pw.printf("\nP Losses (I^2*R)             -              %8.2f MW    @ line %d-%d", max[0], branch.f_bus.get(maxi), branch.t_bus.get(maxi));
				min = loss.getImaginaryPart().getMinLocation();
				max = loss.getImaginaryPart().getMaxLocation();
				mini = new Double(min[1]).intValue();
				maxi = new Double(max[1]).intValue();
				pw.printf("\nQ Losses (I^2*X)             -              %8.2f MVAr  @ line %d-%d", max[0], branch.f_bus.get(maxi), branch.t_bus.get(maxi));
			}
			if (isOPF) {
				min = bus.lam_P.getMinLocation();
				max = bus.lam_P.getMaxLocation();
				mini = new Double(min[1]).intValue();
				maxi = new Double(max[1]).intValue();
				pw.printf("\nLambda P        %8.2f $/MWh @ bus %-4d   %8.2f $/MWh @ bus %-4d", min[0], bus.bus_i.get(mini), max[0], bus.bus_i.get(maxi));
				min = bus.lam_Q.getMinLocation();
				max = bus.lam_Q.getMaxLocation();
				mini = new Double(min[1]).intValue();
				maxi = new Double(max[1]).intValue();
				pw.printf("\nLambda Q        %8.2f $/MWh @ bus %-4d   %8.2f $/MWh @ bus %-4d", min[0], bus.bus_i.get(mini), max[0], bus.bus_i.get(maxi));
			}
			pw.printf("\n");
		}

		if (OUT_AREA_SUM) {
			pw.printf("\n================================================================================");
			pw.printf("\n|     Area Summary                                                             |");
			pw.printf("\n================================================================================");
			pw.printf("\nArea  # of      # of Gens        # of Loads         # of    # of   # of   # of");
			pw.printf("\n Num  Buses   Total  Online   Total  Fixed  Disp    Shunt   Brchs  Xfmrs   Ties");
			pw.printf("\n----  -----   -----  ------   -----  -----  -----   -----   -----  -----  -----");
			for (int i = 0; i < s_areas.length; i++) {
				int a = s_areas[i];
				int[] ib = util.nonzero(bus.bus_area.copy().assign(ifunc.equals(a)));

				IntMatrix1D a_gbus = bus.bus_area.viewSelection( e2i.viewSelection(gen.gen_bus.toArray()).toArray() ).copy().assign(ifunc.equals(a));
				int[] ig = util.nonzero(a_gbus.copy().assign(notload, ifunc.and));
//				ig = find(bus(e2i(gen(:, GEN_BUS)), BUS_AREA) == a & ~isload(gen));
				int[] igon = util.nonzero(a_gbus.copy().assign(gen.gen_status, ifunc.and).assign(notload, ifunc.and));
//				igon = find(bus(e2i(gen(:, GEN_BUS)), BUS_AREA) == a & gen(:, GEN_STATUS) > 0 & ~isload(gen));
				int[] ildon = util.nonzero(a_gbus.copy().assign(gen.gen_status, ifunc.and).assign(isload, ifunc.and));
//				ildon = find(bus(e2i(gen(:, GEN_BUS)), BUS_AREA) == a & gen(:, GEN_STATUS) > 0 & isload(gen));

				IntMatrix1D a_bus = bus.bus_area.copy().assign(ifunc.equals(a));
				IntMatrix1D hasload = util.intm( bus.Pd.copy().assign(dfunc.equals(0)) ).assign(ifunc.equals(0));
				hasload.assign(util.intm( bus.Qd.copy().assign(dfunc.equals(0)) ).assign(ifunc.equals(0)), ifunc.or);
				int[] inzld = util.nonzero(a_bus.copy().assign(hasload, ifunc.and));
//				inzld = find(bus(:, BUS_AREA) == a & (bus(:, PD) | bus(:, QD)));
				IntMatrix1D hasshunt = util.intm( bus.Gs.copy().assign(dfunc.equals(0)) ).assign(ifunc.equals(0));
				hasshunt.assign(util.intm( bus.Bs.copy().assign(dfunc.equals(0)) ).assign(ifunc.equals(0)), ifunc.or);
				int[] inzsh = util.nonzero(a_bus.copy().assign(hasshunt, ifunc.and));
//				inzsh = find(bus(:, BUS_AREA) == a & (bus(:, GS) | bus(:, BS)));

				IntMatrix1D a_fbus = bus.bus_area.viewSelection( e2i.viewSelection(branch.f_bus.toArray()).toArray() ).copy().assign(ifunc.equals(a));
				IntMatrix1D a_tbus = bus.bus_area.viewSelection( e2i.viewSelection(branch.t_bus.toArray()).toArray() ).copy().assign(ifunc.equals(a));
				int[] ibrch = util.nonzero(a_fbus.copy().assign(a_tbus, ifunc.and));
//				ibrch = find(bus(e2i(branch(:, F_BUS)), BUS_AREA) == a & bus(e2i(branch(:, T_BUS)), BUS_AREA) == a);
				int[] in_tie = util.nonzero( a_fbus.copy().assign(a_tbus.copy().assign(ifunc.equals(0)), ifunc.and) );
//				in_tie = find(bus(e2i(branch(:, F_BUS)), BUS_AREA) == a & bus(e2i(branch(:, T_BUS)), BUS_AREA) ~= a);
				int[] out_tie = util.nonzero( a_fbus.copy().assign(ifunc.equals(0)).assign(a_tbus, ifunc.and) );
//				out_tie = find(bus(e2i(branch(:, F_BUS)), BUS_AREA) ~= a & bus(e2i(branch(:, T_BUS)), BUS_AREA) == a);
				int nxfmr;
				if (xfmr.length == 0) {
					nxfmr = 0;
				} else {
					a_fbus = bus.bus_area.viewSelection( e2i.viewSelection(branch.f_bus.viewSelection(xfmr).toArray()).toArray() ).copy().assign(ifunc.equals(a));
					a_tbus = bus.bus_area.viewSelection( e2i.viewSelection(branch.t_bus.viewSelection(xfmr).toArray()).toArray() ).copy().assign(ifunc.equals(a));
					nxfmr = util.nonzero(a_fbus.copy().assign(a_tbus, ifunc.and)).length;
				}
				pw.printf("\n%3d  %6d   %5d  %5d   %5d  %5d  %5d   %5d   %5d  %5d  %5d",
					a, ib.length, ig.length, igon.length,
					inzld.length + ildon.length, inzld.length, ildon.length,
					inzsh.length, ibrch.length, nxfmr, in_tie.length + out_tie.length);
			}
			pw.printf("\n----  -----   -----  ------   -----  -----  -----   -----   -----  -----  -----");
			pw.printf("\nTot: %6d   %5d  %5d   %5d  %5d  %5d   %5d   %5d  %5d  %5d",
				nb, allg.length, ong.length, nzld.length + onld.length,
				nzld.length, onld.length, nzsh.length, nl, xfmr.length, ties.length);
			pw.printf("\n");

			pw.printf("\nArea      Total Gen Capacity           On-line Gen Capacity         Generation");
			pw.printf("\n Num     MW           MVAr            MW           MVAr             MW    MVAr");
			pw.printf("\n----   ------  ------------------   ------  ------------------    ------  ------");
			for (int i = 0; i < s_areas.length; i++) {
				int a = s_areas[i];

				IntMatrix1D a_gbus = bus.bus_area.viewSelection( e2i.viewSelection(gen.gen_bus.toArray()).toArray() ).copy().assign(ifunc.equals(a));
				int[] ig = util.nonzero(a_gbus.copy().assign(notload, ifunc.and));
				int[] igon = util.nonzero(a_gbus.copy().assign(gen.gen_status, ifunc.and).assign(notload, ifunc.and));

				pw.printf("\n%3d   %7.1f  %7.1f to %-7.1f  %7.1f  %7.1f to %-7.1f   %7.1f %7.1f",
					a, gen.Pmax.viewSelection(ig).zSum(), gen.Qmin.viewSelection(ig).zSum(), gen.Qmax.viewSelection(ig).zSum(),
					gen.Pmax.viewSelection(igon).zSum(), gen.Qmin.viewSelection(igon).zSum(), gen.Qmax.viewSelection(igon).zSum(),
					gen.Pg.viewSelection(igon).zSum(), gen.Qg.viewSelection(igon).zSum() );
			}
			pw.printf("\n----   ------  ------------------   ------  ------------------    ------  ------");
			pw.printf("\nTot:  %7.1f  %7.1f to %-7.1f  %7.1f  %7.1f to %-7.1f   %7.1f %7.1f",
					gen.Pmax.viewSelection(allg).zSum(), gen.Qmin.viewSelection(allg).zSum(), gen.Qmax.viewSelection(allg).zSum(),
					gen.Pmax.viewSelection(ong).zSum(), gen.Qmin.viewSelection(ong).zSum(), gen.Qmax.viewSelection(ong).zSum(),
					gen.Pg.viewSelection(ong).zSum(), gen.Qg.viewSelection(ong).zSum() );
			pw.printf("\n");

			pw.printf("\nArea    Disp Load Cap       Disp Load         Fixed Load        Total Load");
			pw.printf("\n Num      MW     MVAr       MW     MVAr       MW     MVAr       MW     MVAr");
			pw.printf("\n----    ------  ------    ------  ------    ------  ------    ------  ------");
			DoubleMatrix1D Qlim = gen.Qmin.copy().assign(dfunc.equals(0)).assign(gen.Qmax, dfunc.mult);
			Qlim.assign(gen.Qmax.copy().assign(dfunc.equals(0)).assign(gen.Qmin, dfunc.mult), dfunc.plus);
//			Qlim = (gen(:, QMIN) == 0) .* gen(:, QMAX) + (gen(:, QMAX) == 0) .* gen(:, QMIN);
			for (int i = 0; i < s_areas.length; i++) {
				int a = s_areas[i];

				IntMatrix1D a_gbus = bus.bus_area.viewSelection( e2i.viewSelection(gen.gen_bus.toArray()).toArray() ).copy().assign(ifunc.equals(a));
				int[] ildon = util.nonzero(a_gbus.copy().assign(gen.gen_status, ifunc.and).assign(isload, ifunc.and));
				IntMatrix1D a_bus = bus.bus_area.copy().assign(ifunc.equals(a));
				IntMatrix1D hasload = util.intm( bus.Pd.copy().assign(dfunc.equals(0)) ).assign(ifunc.equals(0));
				hasload.assign(util.intm( bus.Qd.copy().assign(dfunc.equals(0)) ).assign(ifunc.equals(0)), ifunc.or);
				int[] inzld = util.nonzero(a_bus.copy().assign(hasload, ifunc.and));

//				ildon = find(bus(e2i(gen(:, GEN_BUS)), BUS_AREA) == a & gen(:, GEN_STATUS) > 0 & isload(gen));
//				inzld = find(bus(:, BUS_AREA) == a & (bus(:, PD) | bus(:, QD)));
				pw.printf("\n%3d    %7.1f %7.1f   %7.1f %7.1f   %7.1f %7.1f   %7.1f %7.1f",
					a, -gen.Pmin.viewSelection(ildon).zSum(),
					-Qlim.viewSelection(ildon).zSum(),
					-gen.Pg.viewSelection(ildon).zSum(), -gen.Qg.viewSelection(ildon).zSum(),
					bus.Pd.viewSelection(inzld).zSum(), bus.Qd.viewSelection(inzld).zSum(),
					-gen.Pg.viewSelection(ildon).zSum() + bus.Pd.viewSelection(inzld).zSum(),
					-gen.Qg.viewSelection(ildon).zSum() + bus.Qd.viewSelection(inzld).zSum() );
			}
			pw.printf("\n----    ------  ------    ------  ------    ------  ------    ------  ------");
			pw.printf("\nTot:   %7.1f %7.1f   %7.1f %7.1f   %7.1f %7.1f   %7.1f %7.1f",
					-gen.Pmin.viewSelection(onld).zSum(),
					-Qlim.viewSelection(onld).zSum(),
					-gen.Pg.viewSelection(onld).zSum(), -gen.Qg.viewSelection(onld).zSum(),
					bus.Pd.viewSelection(nzld).zSum(), bus.Qd.viewSelection(nzld).zSum(),
					-gen.Pg.viewSelection(onld).zSum() + bus.Pd.viewSelection(nzld).zSum(),
					-gen.Qg.viewSelection(onld).zSum() + bus.Qd.viewSelection(nzld).zSum() );
			pw.printf("\n");
			pw.printf("\nArea      Shunt Inj        Branch      Series Losses      Net Export");
			pw.printf("\n Num      MW     MVAr     Charging      MW     MVAr       MW     MVAr");
			pw.printf("\n----    ------  ------    --------    ------  ------    ------  ------");
			for (int i = 0; i < s_areas.length; i++) {
				int a = s_areas[i];
				IntMatrix1D a_bus = bus.bus_area.copy().assign(ifunc.equals(a));
				IntMatrix1D hasshunt = util.intm( bus.Gs.copy().assign(dfunc.equals(0)) ).assign(ifunc.equals(0));
				hasshunt.assign(util.intm( bus.Bs.copy().assign(dfunc.equals(0)) ).assign(ifunc.equals(0)), ifunc.or);
				int[] inzsh = util.nonzero(a_bus.copy().assign(hasshunt, ifunc.and));

				IntMatrix1D a_fbus = bus.bus_area.viewSelection( e2i.viewSelection(branch.f_bus.toArray()).toArray() ).copy().assign(ifunc.equals(a));
				IntMatrix1D a_tbus = bus.bus_area.viewSelection( e2i.viewSelection(branch.t_bus.toArray()).toArray() ).copy().assign(ifunc.equals(a));
				int[] ibrch = util.nonzero( a_fbus.copy().assign(a_tbus, ifunc.and).assign(branch.br_status, ifunc.and) );
				int[] in_tie = util.nonzero( a_fbus.copy().assign(a_tbus.copy().assign(ifunc.equals(0)), ifunc.and).assign(branch.br_status, ifunc.and) );
				int[] out_tie = util.nonzero( a_fbus.copy().assign(ifunc.equals(0)).assign(a_tbus, ifunc.and).assign(branch.br_status, ifunc.and) );

				pw.printf("\n%3d    %7.1f %7.1f    %7.1f    %7.2f %7.2f   %7.1f %7.1f",
					a, -bus.Vm.viewSelection(inzsh).copy().assign(dfunc.square).assign(bus.Gs.viewSelection(inzsh), dfunc.mult).zSum(),
					bus.Vm.viewSelection(inzsh).copy().assign(dfunc.square).assign(bus.Bs.viewSelection(inzsh), dfunc.mult).zSum(),
					fchg.viewSelection(ibrch).zSum() + tchg.viewSelection(ibrch).zSum() + fchg.viewSelection(out_tie).zSum() + tchg.viewSelection(in_tie).zSum(),
					loss.viewSelection(ibrch).getRealPart().zSum() + loss.viewSelection(util.cat(in_tie, out_tie)).getRealPart().zSum() / 2,
					loss.viewSelection(ibrch).getImaginaryPart().zSum() + loss.viewSelection(util.cat(in_tie, out_tie)).getImaginaryPart().zSum() / 2,
					branch.Pt.viewSelection(in_tie).zSum()+branch.Pf.viewSelection(out_tie).zSum() - loss.viewSelection(util.cat(in_tie, out_tie)).getRealPart().zSum() / 2,
					branch.Qt.viewSelection(in_tie).zSum()+branch.Qf.viewSelection(out_tie).zSum() - loss.viewSelection(util.cat(in_tie, out_tie)).getImaginaryPart().zSum() / 2 );
			}
			pw.printf("\n----    ------  ------    --------    ------  ------    ------  ------");
			pw.printf("\nTot:   %7.1f %7.1f    %7.1f    %7.2f %7.2f       -       -",
				-bus.Vm.viewSelection(nzsh).assign(dfunc.square).assign(bus.Gs.viewSelection(nzsh), dfunc.mult).zSum(),
				bus.Vm.viewSelection(nzsh).assign(dfunc.square).assign(bus.Bs.viewSelection(nzsh), dfunc.mult).zSum(),
				fchg.zSum() + tchg.zSum(), loss.getRealPart().zSum(), loss.getImaginaryPart().zSum() );
			pw.printf("\n");
		}

	}

}
