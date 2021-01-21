# ThEodorE
ThEodorE tackles the challenge of specifying hybrid behaviors of CPSs, in a way amenable to practical and efficient trace-checking. To reach this goal ThEodorE provides:
-	the **Hybrid Logic of Signals (HLS)**, a new specification language tailored to specifying CPS requirements. HLS allows engineers to express CPS requirements as properties (i.e., specifications) that refer both to the time-stamps and to the indices of the records of CPS traces. In this way, \ourlogic specifications can easily express the behavior of both cyber and physical components, as well as their interactions.
-	 an efficient **trace-checking approach** for properties expressed in HLS (ThEodorE). ThEodorE reduces the problem of checking an HLS property on a trace to a satisfiability problem, which can be solved using off-the-shelf Satisfiability Modulo Theories (SMT) solvers. The latter have efficient decision procedures for several background theories, thus making it possible to check whether a formula expressed in a first-order logic is satisfiable.

A schematic representation of the software components of the ThEodorE trace-checker is presented in the following.

<div align="center">
<img src="./ThEodorE.jpg" alt="ThEodorE">
</div>



ThEodorE takes as input a property φ expressed in HLS and a trace π.

The first step of ThEodorE is to automatically translating property φ and trace π formulae expressed using a target logic L. This translation relies on two translation functions <span style="color:blue">h</span> (for HLS formulae, see Section IV-B) and <span style="color:red">t</span> (for traces, see Section IV-A) and guarantees, given a variable assignment µ, that (π, µ) |= φ iff <span style="color:blue">h</span>(¬φ)∧<span style="color:red">t</span>(π) is not satisfiable.

The second step of ThEodorE is checking the satisfiability of formula ψ ≡ <span style="color:blue">h</span>(¬φ) ∧ <span style="color:red">t</span>(π), expressed in the target logic L using an SMT solver. Based on the condition stated above, when ψ is satisfiable, it means that φ does not hold on the trace π. Vice-versa, when ψ is not satisfiable, it means that φ holds on the trace π.  

The final verdict yielded by ThEodorE can be “satisfied”, “violated” or “unknown”; it is based on the answer of the solver. ThEodorE yields the definitive verdicts “satisfied” or “violated” when the solver returns “UNSAT” or “SAT”, indicating, respectively, that ψ is unsatisfiable or satisfiable. However, the solver may return an “UNKNOWN” answer, since the satisfiability of the underlying target logic L is generally undecidable. In our case, this indicates that no conclusion is drawn on the satisfiability of formula ψ, resulting in an “unknown” verdict returned by ThEodorE.

## Installation
- The ThEodorE plugin, installation instructions, and examples can be found at https://github.com/SNTSVV/ThEodorE/releases within [ThEodorE-v1.0.zip](https://github.com/SNTSVV/ThEodorE/releases/download/v1.0/ThEodorE-v1.0.zip)


## Content of the repository
- This repository contains the source code of ThEodorE
