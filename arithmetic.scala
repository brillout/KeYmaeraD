/*
This file is based on a translation of John Harrison's OCaml code from
_Handbook of Practical Logic and Automated Reasoning_, and falls under
the following license:

IMPORTANT:  READ BEFORE DOWNLOADING, COPYING, INSTALLING OR USING.
By downloading, copying, installing or using the software you agree
to this license.  If you do not agree to this license, do not
download, install, copy or use the software.

Copyright (c) 2003-2007, John Harrison
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

* Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

* The name of John Harrison may not be used to endorse or promote
products derived from this software without specific prior written
permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
SUCH DAMAGE.
*/


package KeYmaeraD


import scala.actors.Actor
import scala.actors.Actor._
//import scala.actors.TIMEOUT

import KeYmaeraD.Util._



abstract class Sign
case class Zero() extends Sign
case class Nonzero() extends Sign
case class Positive() extends Sign
case class Negative() extends Sign

object CV {  
  var lock = new Object();
  var keepGoing = true;
  def start() : Unit = {
    lock.synchronized{ //does scala do this better?
       keepGoing = true;
    }
  }
  def stop() : Unit = {
    lock.synchronized{
       keepGoing = false;
    }
  }
}



class CHAbort() extends Exception
class AssocException() extends Exception
class Failure() extends Exception

final object AM {



  object Integrate {
    def productForm(t: Term): Term = t match {
      case Fn("*", List(t1, Fn("+", List(t2, t3)))) =>
        productForm(
          Fn("+", List(Fn("*", List(t1, t2)),
                       Fn("*", List(t1, t3)))))
      case Fn("*", List(Fn("+", List(t2, t3)), t1)) =>
        productForm(
          Fn("+", List(Fn("*", List(t2, t1)),
                       Fn("*", List(t3, t1)))))
      case Fn("*", List(t1, Fn("-", List(t2, t3)))) =>
        productForm(
          Fn("-", List(Fn("*", List(t1, t2)),
                       Fn("*", List(t1, t3)))))
      case Fn("*", List(Fn("-", List(t2, t3)), t1)) =>
        productForm(
          Fn("-", List(Fn("*", List(t2, t1)),
                       Fn("*", List(t3, t1)))))
      case Fn(f, ts) =>
        Fn(f, ts.map(productForm))
      case Num(n) => Num(n)
      case Var(x) => Var(x)
    }

    // How many times does tocc appear in t?
    def countOccurences(tocc : Term, t : Term): Int = {
      if (t == tocc) { 1 } else {
        t match {
          case Fn(f, ts) => ts.map(x => countOccurences(tocc, x)).sum
          case _ => 0
        }
      }
    }

    // Compute the symbolic integral of t with respect to v.
    def integrate1(v : Term, t : Term) : Term = t match {
      case Fn("+", List(t1,t2)) =>
        Fn("+", List(integrate(v, t1), integrate(v,t2)))
      case Fn("-", List(t1,t2)) =>
        Fn("-", List(integrate(v, t1), integrate(v,t2)))
      case Fn("*", ts) => 
        val n = countOccurences(v, t)
        Fn("*", List(Num(Exact.Rational(1, n+1)),
                     Fn("*", List(v, t))))
      case Fn(f, Nil) => Fn("*", List(t, v))
      case Num(n) => Fn("*", List(t, v))
      case _ => 
        println("unimplemented")
        throw new Error("unimplemented")
    }

    def integrate(v : Term, t : Term) : Term = {
      tsimplify(integrate1(v, productForm(t)))
    }

  } // object Integrate


/* Simplification.
 */



  def tsimplify1( t: Term): Term = t match {
    case Fn("+",List(Num(m), Num(n))) => Num(m + n)
    case Fn("-",List(Num(m), Num(n))) => Num(m - n)
    case Fn("*",List(Num(m), Num(n))) => Num(m * n)
    case Fn("/",List(Num(m), Num(n))) => Num(m / n)
    case Fn("+",List(Num(n), x)) if n.is_zero => x
    case Fn("+",List(x,Num(n))) if n.is_zero => x
    case Fn("-",List(Num(n), x)) if n.is_zero => Fn("-", List(x))
    case Fn("-",List(x, Num(n))) if n.is_zero => x
    case Fn("*",List(Num(n), x)) if n.is_zero => zero
    case Fn("/",List(Num(n), x)) if n.is_zero => zero
    case Fn("*",List(x,Num(n))) if n.is_zero => zero
    case Fn("*",List(Num(n), x)) if n.is_one => x
    case Fn("*",List(x,Num(n))) if n.is_one => x
    case Fn("/",List(x,Num(n))) if n.is_one => x
    case _ => t
  }

  def tsimplify(t: Term): Term = t match {
    case Fn("+",List(e1, e2)) => tsimplify1(
      Fn("+",List(tsimplify(e1), tsimplify(e2))))
    case Fn("*",List(e1, e2)) => tsimplify1(
      Fn("*",List(tsimplify(e1), tsimplify(e2))))
    // Added this case to help diffsolveT.
    case Fn("-",List(tm1, tm2)) if tm1 == tm2 => zero
    case Fn("-",List(e1, e2)) => tsimplify1(
      Fn("-",List(tsimplify(e1), tsimplify(e2))))
    // There should probably be a case for / here.
    case _ => tsimplify1(t)
  }



  def psimplify1(fm: Formula): Formula = fm match {
    case Not(False) => True
    case Not(True) => False
    case Not(Not(p)) => p
    case Binop(And, p,False) => False
    case Binop(And, False,p) => False
    case Binop(And,p,True) => p
    case Binop(And,True,p) => p
    case Binop(Or,p,False) => p
    case Binop(Or,False,p) => p
    case Binop(Or,p,True) => True
    case Binop(Or,True,p) => True
    case Binop(Imp,False,p) => True
    case Binop(Imp,p,True) => True
    case Binop(Imp,True,p) => p
    case Binop(Imp,p, False) => Not(p)
    case Binop(Iff,p, True) => p
    case Binop(Iff,True,p) => p
    case Binop(Iff,False, False) => True
    case Binop(Iff,p, False) => Not(p)
    case Binop(Iff,False,p) => Not(p)
    case _ => fm
  }

  /* Simplify a propositional formula. */

  def psimplify(fm: Formula): Formula = fm match {
    case Not(p) => psimplify1(Not(psimplify(p)))
    case Binop(And,p,q) => psimplify1(Binop(And,psimplify(p),psimplify(q)))
    case Binop(Or,p,q) => psimplify1(Binop(Or,psimplify(p),psimplify(q)))
    case Binop(Imp,p,q) => psimplify1(Binop(Imp,psimplify(p),psimplify(q)))
    case Binop(Iff,p,q) => psimplify1(Binop(Iff,psimplify(p),psimplify(q)))
    case _ => fm
  }






 
  def simplify1(fm: Formula): Formula = fm match {
    case Quantifier(_,_,x,p) => if( fv(p).contains(x) ) fm
                              else p
    case _ => psimplify1(fm)
  }


  /* Simplify a first order formula. */

  def simplify(fm: Formula): Formula = fm match {
    case Not(p) => simplify1(Not(simplify(p)))
    case Binop(c,p,q) => simplify1(Binop(c,simplify(p),simplify(q)))
    case Quantifier(q,c,x,p) => simplify1(Quantifier(q,c,x,simplify(p)))
    case _ => fm
  }

  

  def distrib[A <% Ordered[A]](s1: List[List[A]], s2: List[List[A]])
   : List[List[A]] = {
    setify(allpairs(union[A],s1,s2))
  }

  def purednf(fm: Formula): List[List[Formula]] = fm match {
    case Binop(And,p,q) => distrib(purednf(p),purednf(q))
    case Binop(Or,p,q) => union(purednf(p),purednf(q))
    case _ => List(List(fm))
  }

  // does this list of formulas have a pair f and Not(f)?
  def trivial(lits: List[Formula]): Boolean = {
    val (pos,neg) = lits.partition(positive(_));
    ! intersect(pos, setify(neg.map(negate))).isEmpty
  }




  def simpdnf(fm: Formula): List[List[Formula]] = {
    if(fm == False) Nil else if(fm == True) List(Nil) else {
    val djs = purednf(nnf(fm)).filter((x:List[Formula]) => ! trivial(x));
    djs.filter(d => !(djs.exists(d_1 => psubset(d_1,d))))
    }
  }

  def dnf(fm: Formula): Formula = {
    list_disj(simpdnf(fm).map(list_conj))
  }




  def separate(x: String, cjs: List[Formula]): Formula = {
    val (yes,no) = cjs.partition(c => fv(c).contains(x));
    if(yes == Nil) list_conj(no) 
    else if(no == Nil) Quantifier(Exists,Real,x,list_conj(yes))
    else Binop(And,Quantifier(Exists,Real,x,list_conj(yes)), list_conj(no))
  }
  
  def pushquant(x: String, p: Formula): Formula = {
//    P.print_fol_formula(p);
//    println();
    if(! fv(p).contains(x)) p else {
      val djs = purednf(nnf(p));
      list_disj (djs.map(d => separate(x,d)))
    }
  }

  def miniscope(fm: Formula): Formula = {
    fm match {
    case Not(p) => Not(miniscope(p))
    case Binop(And,p,q) => Binop(And,miniscope(p),miniscope(q))
    case Binop(Or,p,q) => Binop(Or,miniscope(p),miniscope(q))
    case Quantifier(Forall,c,x,p) => Not(pushquant(x,Not(miniscope(p))))
    case Quantifier(Exists,c,x,p) => pushquant(x,miniscope(p))
    case _ => fm
  }
  }



  def eval(fm: Formula, v: Pred => Boolean): Boolean = fm match {
    case False => false
    case True => true
    case Atom(x) => v(x)
    case Not(p) => eval(p,v) unary_!
    case Binop(And,p,q) => eval(p,v) && eval(q,v)
    case Binop(Or,p,q) => eval(p,v) || eval(q,v)
    case Binop(Imp,p,q) => (eval(p,v) unary_! ) || eval(q,v)
    case Binop(Iff,p,q) => eval(p,v) == eval(q,v)
    case _ => 
      throw new Error("nonfirstorder arithmetic")
  }

  val operations: List[(String, (Exact.Num,Exact.Num) => Boolean)] = 
    List(("=", (r,s) => r == s),
         ("/=", (r,s) => r != s),
         ("<", (r,s) => r < s),
         (">", (r,s) => r > s),
         ("<=", (r,s) => r <= s),
         (">=", (r,s) => r >= s))


  def evalc(fm: Formula) : Formula =  {
    onatoms(
      at => at match {
        case R(p,List(Num(n),Num(m))) => 
          try {if(assoc(p,operations)(n,m)) True else False}
          catch { case e => Atom(at)}
        case _ => Atom(at)
      }, fm)
  }


  def mk_and(p: Formula, q: Formula): Formula = Binop(And,p,q);
  def mk_or(p: Formula, q: Formula): Formula = Binop(Or,p,q);

  def conjuncts(fm: Formula): List[Formula] = fm match {
    case Binop(And,p,q) => conjuncts(p) ++ conjuncts(q) 
    case _ => List(fm)
  }

  def disjuncts(fm: Formula): List[Formula] = fm match {
    case Binop(Or,p,q) => disjuncts(p) ++ disjuncts(q) 
    case _ => List(fm)
  }

  // XXX
  def onatoms_HP(f : Pred => Formula, hp : HP) : HP = hp match {
    case _ => hp
  }


  def onatoms(f: Pred => Formula, fm: Formula): Formula  = fm match {
    case Atom(a) => f(a)
    case Not(p) => Not(onatoms(f,p))
    case Binop(c,p,q) => Binop(c,onatoms(f, p), onatoms(f,q))
    case Quantifier(q,c,x,p ) => Quantifier(q,c,x,onatoms(f,p))
    case Modality(m,hp, phi) => Modality(m,onatoms_HP(f,hp), onatoms(f,phi))
    case _ => fm
  }

  def simplify_terms(fm: Formula): Formula = {
    onatoms( fol => fol match {
      case R(r,  List(t1,t2)) => Atom(R(r,List(tsimplify(t1),tsimplify(t2))))
      case _ => throw new Error("simplify terms.")
    }, fm)
  }

  def overatoms[B](f: Pred => B => B, fm: Formula, b: B): B = fm match {
    case Atom(a) => f(a)(b)
    case Not(p) => overatoms(f,p,b)
    case Binop(_,p,q) => overatoms(f, p, overatoms(f,q,b))
    case Quantifier(_,_,x,p ) => overatoms(f, p, b)
    case _ => b
  }

  def atom_union[A <% Ordered[A]](f: Pred => List[A], fm: Formula): List[A] = {
    setify(overatoms( (h:Pred) => (t:List[A]) => f(h) ++ t, fm, Nil))
  }

   
  def list_conj(l: List[Formula]) : Formula = l match {
    case Nil => True
    case f::Nil => f
    case f::fs => Binop(And,f, list_conj(fs))
  }

  def list_disj(l: List[Formula]) : Formula = l match {
    case Nil => False
    case f::Nil => f
    case f::fs => Binop(Or,f, list_disj(fs))
  }



  def negative(fm: Formula) : Boolean = fm match {
    case Not(p) => true
    case _ => false
  }

  def positive(fm: Formula) : Boolean = fm match {
    case Not(p) => false
    case _ => true
  }

  def negate(fm: Formula) : Formula = fm match {
    case Not(p) => p
    case p => Not(p)
  }

  def cnnf(lfn:  Formula => Formula ) : Formula => Formula  =  {
    def cnnf_aux(fm: Formula): Formula = fm match {
      case Binop(And,p,q) => Binop(And,cnnf_aux(p), cnnf_aux(q))
      case Binop(Or,p,q) => Binop(Or,cnnf_aux(p), cnnf_aux(q))
      case Binop(Imp,p,q) => Binop(Or,cnnf_aux(Not(p)), cnnf_aux(q))
      case Binop(Iff,p,q) => Binop(Or,Binop(And,cnnf_aux(p), cnnf_aux(q)),
                          Binop(And,cnnf_aux(Not(p)), cnnf_aux(Not(q))))
      case Not(Not(p)) => cnnf_aux(p)
      case Not(Binop(And,p,q)) => Binop(Or,cnnf_aux(Not(p)), cnnf_aux(Not(q)))
      case Not(Binop(Or,Binop(And,p,q),Binop(And,p_1,r))) if p_1 == negate(p) =>
        Binop(Or,cnnf_aux(Binop(And,p,Not(q))), cnnf_aux(Binop(And,p_1,Not(r))))
      case Not(Binop(Or,p,q)) => Binop(And,cnnf_aux(Not(p)),cnnf_aux(Not(q)))
      case Not(Binop(Imp,p,q)) => Binop(And,cnnf_aux(p), cnnf_aux(Not(q)))
      case Not(Binop(Iff,p,q)) => Binop(Or,Binop(And,cnnf_aux(p),cnnf_aux(Not(q))),
                               Binop(And,cnnf_aux(Not(p)),cnnf_aux(q)))
      case _ => lfn(fm)
    }
    fm => simplify(cnnf_aux(simplify(fm)))
  }
        

      



  val rZero = new Exact.Rational(0);
  val rOne = new Exact.Rational(1);

  val zero = Num(rZero)
  val one = Num(rOne)




/* Polynomial utilities.
 */

  def poly_add(vars: List[String], pol1: Term, pol2: Term): Term = 
    (pol1,pol2) match {
     case (Fn("+", List(c, Fn("*",List(Var(x),p)))),
           Fn("+", List(d, Fn("*",List(Var(y),q))))) =>
             if(earlier(vars,x,y)) poly_ladd(vars, pol2, pol1)
             else if(earlier(vars,y,x)) poly_ladd(vars, pol1,pol2)
             else {
               val e = poly_add(vars,c,d);
               val r = poly_add(vars,p,q);
               if(r == zero) e
               else Fn("+", List(e, Fn("*", List(Var(x), r))))
             }
      case (_,Fn("+",_)) => poly_ladd(vars,pol1,pol2)
      case (Fn("+",_),_) => poly_ladd(vars,pol2,pol1)
      case (Num(n),Num(m)) => Num(n + m)
      case _ =>   zero
    }
  
  def poly_ladd(vars: List[String], pol1: Term, pol2: Term): Term = 
    pol2 match {
      case (Fn("+",List(d,Fn("*",List(Var(y),q))))) =>
        Fn("+",List(poly_add(vars, pol1, d), Fn("*", List(Var(y), q))))
      case _ => throw new Error("poly_ladd: malformed input")
    }

  def poly_neg(q: Term): Term = q match {
    case Fn("+",List(c,Fn("*",List(Var(x),p)))) =>
      Fn("+",List(poly_neg(c), Fn("*",List(Var(x), poly_neg(p)))))
    case Num(n) => Num(-n)
    case _ => throw new Error("impossible")
  }

  def poly_sub(vars: List[String], p: Term, q: Term): Term = {
    val q1 = poly_neg(q);
    val r =poly_add(vars, p, poly_neg(q));
    r
  }

  def poly_mul(vars: List[String], pol1: Term, pol2: Term): Term = 
    (pol1,pol2) match {
     case (Fn("+", List(c, Fn("*",List(Var(x),p)))),
           Fn("+", List(d, Fn("*",List(Var(y),q))))) =>
             if(earlier(vars,x,y)) poly_lmul(vars, pol2, pol1)
             else poly_lmul(vars, pol1, pol2)
      case (Num(n), _) if n.is_zero => zero
      case (_,Num(n)) if n.is_zero => zero
      case (_,Fn("+",_)) => poly_lmul(vars,pol1,pol2)
      case (Fn("+",_),_) => poly_lmul(vars,pol2,pol1)
      case (Num(n),Num(m)) => Num(n * m)
      case _ => zero
    }
  def poly_lmul(vars: List[String], pol1: Term, pol2: Term): Term = 
    pol2 match {
      case (Fn("+",List(d,Fn("*",List(Var(y),q))))) =>
        poly_add(vars, poly_mul(vars, pol1, d),
                 Fn("+",List(zero,
                             Fn("*",List(Var(y), poly_mul(vars,pol1,q))))))
      case _ => throw new Error("poly_lmul: malformed input")
    }

  def funpow[A](n: Int, f: A => A, x: A): A = {
    if( n < 1 ) x
    else funpow(n-1, f, f(x))
  }


  def poly_pow(vars: List[String], p: Term, n: Int): Term = {
    funpow(n, (q:Term) => poly_mul(vars,p,q), one)
  }

/* I don't think we need this.
  def poly_div(vars: List[String], p: Term, q: Term) = q match {
    case Num(n) =>  poly_mul(vars, p, Num(1.0/n) ... ?

*/

  def poly_var(x: String): Term = {
    Fn("+",List(zero,Fn("*",List(Var(x), one))))
  }


  /* Put tm into canonical form.
   */
  def polynate(vars: List[String], tm: Term): Term = tm match {
    case Var(x) => poly_var(x)
    case Fn("-", t::Nil) => poly_neg(polynate(vars,t))
    case Fn("+", List(s,t)) => poly_add(vars,polynate(vars,s),
					polynate(vars,t))
    case Fn("-", List(s,t)) => poly_sub(vars,polynate(vars,s),
					polynate(vars,t))
    case Fn("*", List(s,t)) => poly_mul(vars,polynate(vars,s),
					polynate(vars,t))
    
    case Fn("/", List(Num(n),Num(m))) => Num(n / m)
    
    case Fn("^", List(p,Num(n))) => 
      poly_pow(vars,polynate(vars,p),n.intValue) //n is a Rational.
    case Num(n) => tm
    case _ => throw new Error("Unknown term: " + tm)
  }


  def polyatom(vars: List[String], fm: Formula): Formula = fm match {
    case Atom(R(a,List(s,t))) =>
      val r = Atom(R(a,List(polynate(vars,Fn("-",List(s,t))),zero)));
      r
    case _ => throw new Error("polyatom: not an atom.")
  }



  def coefficients(vars: List[String], p: Term): List[Term] = p match {
    case Fn("+", List(c, Fn("*", List(Var(x), q)))) if x == vars.head =>
      c::(coefficients(vars,q))
    case _ => List(p)
  }

  def degree(vars: List[String], p: Term): Int = {
    (coefficients(vars,p).length - 1)
  }

  def is_constant(vars: List[String], p: Term): Boolean = {
    degree(vars,p) == 0
  }
  
  def head(vars: List[String], p: Term): Term = {
    coefficients(vars,p).last
  }

  def behead(vars: List[String], tm: Term): Term = tm match {
    case Fn("+",List(c,Fn("*",List(Var(x),p)))) if x == vars.head =>
      val p1 = behead(vars,p);
      if(p1 == zero) c else Fn("+",List(c,Fn("*",List(Var(x),p1))))
    case _ => zero
  }

  def poly_cmul(k: Exact.Num, p: Term): Term = p match {
    case Fn("+", List(c, Fn("*", List( Var(x), q)))) =>
      Fn("+", List(poly_cmul(k,c),
                   Fn("*",List(Var(x),
                               poly_cmul(k,q)))))
    case Num(n) => Num(n * k)
    case _ => throw new Error("poly_cmul: non-canonical term" + p)
  }

  def headconst(p: Term): Exact.Num = p match {
    case Fn("+",List(c,Fn("*",List(Var(x),q)))) => headconst(q)
    case Num(n) => n
    case _ => throw new Error("headconst: malformed polynomial")
  }


  def monic(p: Term): (Term,Boolean) = {
    val h = headconst(p);
    if(h.is_zero) (p,false)
    else (poly_cmul(rOne / h, p), h < rZero)
  }




  val pdivide: List[String] => Term => Term =>  (Int, Term) = {
    def shift1(x: String): Term => Term = p =>  Fn("+",List(zero,
                                                       Fn("*",List(Var(x),
                                                                   p))));
    def pdivide_aux(vars: List[String], 
                    a: Term, 
                    n: Int, 
                    p: Term,
                    k: Int,
                    s: Term): (Int, Term) = {
      if(s == zero) (k,s) else {
        val b = head(vars, s);
        val m = degree(vars, s);
        if(m < n) (k,s) else {
          val p_1 = funpow(m-n, shift1(vars.head), p);
          if(a == b) pdivide_aux(vars,a,n,p,k,poly_sub(vars,s,p_1))
          else pdivide_aux(vars,a,n,p,k+1,
                           poly_sub(vars,poly_mul(vars,a,s),
                                    poly_mul(vars,b,p_1)))
        }
      }
    };
    vars => s => p => pdivide_aux(vars, head(vars,p), degree(vars,p), p, 0, s)
  }

  

  def poly_diffn(x: Term, n: Int, p: Term): Term = p match {
    case Fn("+", List(c, Fn("*", List(y,q)))) if y == x => 
      Fn("+", List(poly_cmul(new Exact.Rational(n), c), 
                   Fn("*", List(x, poly_diffn(x,n+1,q)))))
    case _ => poly_cmul( new Exact.Rational(n), p)
  }

  def poly_diff(vars: List[String], p: Term): Term = p match {
    case Fn("+", List(c, Fn("*", List(Var(x), q)))) if x == vars.head =>
      poly_diffn(Var(x), 1, q)
    case _ => zero
  }


/* End polynomical utilities.
 */


  def swap(swf: Boolean, s: Sign): Sign = {
    if(!swf) s else s match {
      case Positive() => Negative()
      case Negative() => Positive()
      case _ => s
    }
  }





  def qelim(bfn: Formula => Formula, x: String, p: Formula): Formula = {
    val cjs = conjuncts(p);
    val (ycjs, ncjs) = cjs.partition(c => fv(c).contains(x));
    if(ycjs == Nil) p else {
      val q = bfn(Quantifier(Exists,Real, x, list_conj(ycjs)));
      val r = ncjs.foldLeft(q)(mk_and)
      print("|");
      r
    }
  }


  def lift_qelim(afn: (List[String], Formula) => Formula,
                 nfn: Formula => Formula,
                 qfn: List[String] => Formula => Formula) : 
  Formula => Formula = {
    def qelift(vars: List[String], fm: Formula): Formula = fm match {
      case Atom(R(_,_)) => afn(vars,fm)
      case Not(p) => Not(qelift(vars,p))
      case Binop(c,p,q) => 
        Binop(c,qelift(vars,p), qelift(vars,q))
      case Quantifier(Forall,Real,x,p) => 
        Not(qelift(vars,Quantifier(Exists,Real,x,Not(p))))
      case Quantifier(Exists,Real,x,p) => 
        val djs = disjuncts(nfn(qelift(x::vars,p)));
        println("In qelift.  Number of disjuncts = " + djs.length);
        print("["); 
        for(i <- 0 until djs.length){ print(".");}
        print("]\u0008");
        for(i <- 0 until djs.length){ print("\u0008");}
        val djs2 = djs.map(p1 => qelim(qfn(vars), x, p1));
        val r = list_disj(djs2)
        println("]");
        r
//        list_disj(Parallel.pmap(djs, ((p1:Formula) => qelim(qfn(vars), x, p1))))
      case _ => fm
    }
    fm => {
      val m = miniscope(fm);
      val f = fv(fm);
      val q = qelift( f, m);
      val r = simplify(q)
      r
    }
   }



  class FindSignFailure() extends Exception;

  def findsign(sgns: List[(Term,Sign)], p: Term): Sign = 
    try {
      val (p_1,swf) = monic(p);
      swap(swf,assoc(p_1,sgns))
    } catch {
      case e => throw new FindSignFailure()
    }

  def assertsign(sgns: List[(Term,Sign)], pr: (Term,Sign)): List[(Term,Sign)]
  = {
    val (p,s) = pr;
    if( p == zero ) {
      if(s == Zero()) sgns 
      else throw new Error("assertsign") }
    else {
    val (p_1,swf) = monic(p);
    val s_1 = swap(swf,s);
    val s_0 = try { assoc(p_1,sgns) } catch { case e => s_1};
    if(s_1 == s_0 || (s_0 == Nonzero() && (s_1==Positive() || s_1==Negative())))
      (p_1,s_1)::(sgns filterNot ( List((p_1,s_0)) contains ))
    else throw new Error("assertsign 1")
    }
  }

  final def split_zero(sgns: List[(Term,Sign)], pol: Term, 
                 cont_z: List[(Term,Sign)] => Formula,
                 cont_n: List[(Term,Sign)] => Formula) : Formula 
  = try {
      val z = findsign(sgns,pol);
      (if(z == Zero()) cont_z else cont_n)(sgns)
  } catch {
    case f: FindSignFailure => 
      val eq = Atom(R("=",List(pol,zero)));
      Binop(Or,Binop(And,eq, cont_z(assertsign(sgns, (pol,Zero())))),
         Binop(And,Not(eq), cont_n(assertsign(sgns,(pol,Nonzero())))))
  }


  val rel_signs = List(("=", List(Zero())),
                       ("<=", List(Zero(), Negative())),
                       (">=", List(Zero(), Positive())),
                       ("<", List(Negative())),
                       (">", List(Positive())) )





 def testform(pmat: List[(Term, Sign)], fm: Formula): Boolean = {
//   println("in testform. pmat = ");
//   pmat.map( x => {print("("); 
//                   P.printert(x._1); 
//                   println(", " + x._2 + ")");});
//   println("fm = ");
//   P.print_fol_formula(fm);
//   println();
    def f(r: Pred): Boolean = r match {
      case R(a,List(p,z)) => 
	mem(assoc(p, pmat), assoc(a, rel_signs))
      case _ => throw new Error("testform: bad Pred:" + r)
    };
    eval(fm, f)
  }


  def inferpsign(pr: (List[Sign], List[Sign])): List[Sign] = pr match {
    case (pd,qd) =>
      try {
        val i = index(Zero(), pd);
        el(i,qd)::pd
      } catch {
        case e:Failure => Nonzero() :: pd
      }
  }

  def condense(ps: List[List[Sign]]): List[List[Sign]] = ps match {
    case int::pt::other => 
      val rest = condense(other);
      if(mem(Zero(), pt)) int::pt::rest
      else rest
    case _ => ps
  }


  def inferisign(ps: List[List[Sign]]): List[List[Sign]] = ps match {
    case ((x@(l::ls))::(_::ints)::(pts@((r::rs)::xs))) =>
      (l,r) match {
        case (Zero(), Zero()) => throw new Error("inferisign: inconsistent")
        case (Nonzero() ,_) 
          |  (_, Nonzero()) => throw new Error("inferisign: indeterminate")
        case (Zero(),_) => x::(r::ints)::inferisign(pts)
        case (_,Zero()) => x::(l::ints)::inferisign(pts)
        case (Negative(), Negative()) 
          |  (Positive(), Positive()) =>  
            x::(l::ints)::inferisign(pts)
        case _ => x::(l::ints)::(Zero()::ints)::(r::ints)::inferisign(pts)
      }
    case _ => ps
  }



  def dedmatrix(cont: List[List[Sign]] => Formula,
                 mat: List[List[Sign]]) : Formula = {
    val l = (mat.head).length / 2;
    val mat1 = condense(mat.map((lst:List[Sign])=>inferpsign(lst.splitAt(l))));
//    val mat1 = condense(Parallel.pmap(mat,(lst:List[Sign])=>inferpsign(lst.splitAt(l))));
    val mat2 = List(swap(true, el(1,mat1.head)))::
                          (mat1 ++ List(List(el(1,mat1.last))));
    val mat3 = inferisign(mat2).tail.init;
    cont(condense(mat3.map((l:List[Sign]) => l.head :: l.tail.tail)))      
  }

  def pdivide_pos(vars: List[String], sgns: List[(Term,Sign)], 
                 s: Term, p: Term): Term
   = {
     val a = head(vars,p);
     val (k,r) = pdivide(vars)(s)(p);
     val sgn = findsign(sgns,a);
     if(sgn == Zero()) throw new Error("pdivide_pos: zero head coefficient.")
     else if(sgn == Positive() || (k % 2) == 0) r
     else if(sgn == Negative()) poly_neg(r)
     else poly_mul(vars,a,r)
   }

  def split_sign(sgns: List[(Term,Sign)], pol: Term, 
                 cont: List[(Term,Sign)] => Formula) : Formula = 
    findsign(sgns, pol) match {
      case Nonzero() => 
        val fm = Atom(R(">",List(pol,zero)));
        Binop(Or,Binop(And,fm,cont(assertsign(sgns,(pol,Positive())))),
           Binop(And,Not(fm),cont(assertsign(sgns,(pol,Negative())))))
      case _ => cont(sgns)
    }

  final def split_trichotomy(sgns: List[(Term,Sign)], 
                       pol: Term,
                       cont_z: List[(Term,Sign)] => Formula,
                       cont_pn: List[(Term,Sign)] => Formula) : Formula =
    split_zero(sgns,pol,cont_z,(s_1 => split_sign(s_1,pol,cont_pn)))


/* inlined
  final def monicize(vars: List[String], 
                     pols: List[Term],
                     cont: List[List[Sign]] => Formula,
                     sgns: List[(Term,Sign)] ): Formula = {
     val (mols,swaps) = List.unzip(pols.map(monic));
     val sols = setify(mols);
     val indices = mols.map(p => index(p, sols));
     def transform(m: List[Sign]) : List[Sign] = {
       (swaps zip indices).map( pr => swap(pr._1, el(pr._2, m)))}
     val (cont_1 : (List[List[Sign]] => Formula)) = mat => cont(mat.map(transform));
     matrix(vars,sols,cont_1,sgns)
     }
*/


  final def casesplit(vars: List[String],
                dun: List[Term],
                pols: List[Term],
                cont: List[List[Sign]] => Formula):
                List[(Term,Sign)]  => Formula = sgns => pols match {
//    case Nil => monicize(vars,dun,cont,sgns)
//    case Nil => matrix(vars,dun,cont,sgns)
    case Nil => val (mols,swaps) = dun.map(monic).unzip;
                val sols = setify(mols);
                val indices = mols.map(p => index(p, sols));
                def transform(m: List[Sign]) : List[Sign] = {
                  (swaps zip indices).map( pr => swap(pr._1, el(pr._2, m)))}
                val (cont_1 : (List[List[Sign]] => Formula)) = mat => cont(mat.map(transform));
                matrix(vars,sols,cont_1,sgns)
    case p::ops => 
      split_trichotomy(sgns,head(vars,p),
                       (if(is_constant(vars,p)) delconst(vars,dun,p,ops,cont)
                        else casesplit(vars,dun,behead(vars,p)::ops,cont)),
                       (if(is_constant(vars,p)) delconst(vars,dun,p,ops,cont)
                        else casesplit(vars,dun++List(p),ops,cont)))
  }

  final def delconst(vars: List[String], 
               dun: List[Term], 
               p: Term, 
               ops: List[Term],
               cont: List[List[Sign]] => Formula) :
               List[(Term,Sign)] => Formula = sgns => {
    def cont_1(m: List[List[Sign]]): Formula = 
      cont(m.map((rw:List[Sign]) => insertat(dun.length,findsign(sgns,p),rw)));
    casesplit(vars,dun,ops,cont_1)(sgns)
  }



  final def matrix(vars: List[String],
             pols: List[Term],
             cont: List[List[Sign]] => Formula,
             sgns: List[(Term,Sign)]): Formula = {
//    CV.lock.synchronized{
      if(CV.keepGoing == false) throw new CHAbort();
//    }

    if(pols == Nil) try { cont(List(Nil)) } catch {case e => False} else {
    /* find the polynomial of highest degree */
    val (p,_) = pols.foldLeft[(Term,Int)](zero,-1)(
      (bst:(Term,Int),ths:Term) => {val (p_1,n_1) = bst; 
                                    val n_2 =  degree(vars, ths);
                                    if(n_2 > n_1) (ths,n_2) else bst});
    val p_1 = poly_diff(vars,p);
    val i = index(p,pols);
    val qs = {val (p1,p2) = pols.splitAt(i);
              p_1::p1 ++ p2.tail};
//    println("in matrix. number of divisions to perform = " + qs.length);
    val gs = qs.map((p_3:Term) => pdivide_pos(vars,sgns,p,p_3));
//    val gs = Parallel.pmap(qs,((p_3:Term) => pdivide_pos(vars,sgns,p,p_3)));
    def cont_1(m: List[List[Sign]]): Formula = 
      cont(m.map(l => insertat(i,l.head,l.tail)));
    casesplit(vars, Nil, qs ++ gs, ls => dedmatrix(cont_1,ls))(sgns)
                                      
    }
  }

  val init_sgns:List[(Term,Sign)] = List((one, Positive()),
                                         (zero, Zero()));

  def basic_real_qelim(vars: List[String]): Formula => Formula 
  = fm => fm match {
    case Quantifier(Exists,Real,x,p) =>
      val pols = atom_union(
        fm1 => fm1 match{case R(a,List(t,Num(n))) if n.is_zero => List(t)
                         case _ => Nil},
        p);
      val cont = (mat:List[List[Sign]]) => 
        if(mat.exists(m => testform(pols.zip(m),p))) True else False;
      casesplit(x::vars, Nil, pols, cont)(init_sgns)
    case _ => 
      throw new Error("impossible")
  }



  def real_elim(fm: Formula): Formula = {
    simplify(evalc(lift_qelim(polyatom,
                              fm1 => simplify(evalc(fm1)),
                              basic_real_qelim)(fm)))
  }


  /* better version that first converts to dnf */
  def real_elim2(fm: Formula): Formula = {
    simplify(evalc(lift_qelim(polyatom,
                              fm1 => dnf(cnnf( (x:Formula)=>x)(evalc(fm1))),
                              basic_real_qelim)(fm)))
  }

  def univ_close(fm: Formula): Formula = {
    val fvs = fv(fm);
    fvs.foldRight(fm) ((v,fm1) => Quantifier(Forall,Real,v,fm1))
  }


  def unaryFns_Term(tm : Term): List[String] = tm match {
    case Fn(f, Nil) => List(f)
    case Fn(f, args) => 
      args.map(unaryFns_Term).flatten.distinct
    case _ => Nil
  }
  
  def unaryFns_Pred(fol: Pred) : List[String] = fol match {
    case R(r, args) => 
      args.map(unaryFns_Term).flatten.distinct
  }

  def replaceUnaryFns_Term(tm : Term): Term = tm match {
    case Fn(f, Nil) => Var(f)
    case Fn(f, args) => Fn(f, args.map(replaceUnaryFns_Term))
    case _ => tm
  }
  
  def replaceUnaryFns_Pred(fol: Pred) : Pred = fol match {
    case R(r, args) => 
      R(r,args.map(replaceUnaryFns_Term))
  }

  // free variables are existentially quantified.
  def makeQEable(fm : Formula) : Formula = {
    val fvs = fv(fm);
    val unary_fns = overatoms(fol => (lst: List[String]) 
                                 => unaryFns_Pred(fol)++lst  ,
                                  fm,Nil).distinct
    // XXX should uniqify
    val fm0 = onatoms(
      fol => Atom(replaceUnaryFns_Pred(fol)),fm)
    val fm1 = unary_fns.foldRight(fm0)((v,f) => Quantifier(Forall,Real,v,f))
    fvs.foldRight(fm1) ((v,f) => Quantifier(Exists,Real,v,f))
  }


  def real_elim_goal(fm: Formula): Boolean = {
    val fm0 = real_elim(fm);
    fm0 match {
      case True => true
      case _ => false
    }
  }


  def real_elim_goal_univ_closure(fm: Formula): Boolean = {
    val fm0 = real_elim(univ_close(fm));
    fm0 match {
      case True => true
      case _ => false
    }
  }
  


  @throws(classOf[CHAbort])
  def real_elim_try_universal_closure(fm: Formula, opt: Int): Formula = {
    val re = if(opt == 1) real_elim _ else real_elim2 _ ;
    val fm0 = simplify(evalc(fm));
//    println("after initial simplification:");
//    P.print_fol_formula(fm0);
    println();
    val fm1 =  re(fm0);
    if(fv(fm1).length < fv(fm).length || fv(fm).length == 0 )
      fm1
      else {
        println("; trying universal closure");
        val fm2 = re(univ_close(fm0));
        if(fm2 == True) True else fm1
      }

  }
  

  def elim_fractional_literals(fm: Formula): Formula = {
    def elim_fraction_term : Term => Term = tm => tm match {
      case Num(Exact.Rational(p,q)) => 
        if(p == BigInt(0)) Num(Exact.Integer(0))
        else if (q == BigInt(1)) Num(Exact.Integer(p))
	else  Fn("/", List(Num(Exact.Integer(p)), Num(Exact.Integer(q))))
      case Fn(f,args) => Fn(f, args.map(elim_fraction_term))
      case _ => tm
    }
    def elim_fraction_atom : Pred => Formula = fol => fol match {
      case R(s, List(t1,t2)) => 
        Atom(R(s, List(elim_fraction_term(t1),elim_fraction_term(t2))))
      case _ => Atom(fol)
    }
    onatoms(elim_fraction_atom, fm)
  }




  def test = poly_pow(List(), 
                      Fn("+",List(one, Fn("*",List(Var("x"), one)))),5);
//  def test1 = polynate(List("x"), P.parset("1 + x"));


  def test_qelim(func: Int, fm: Formula): Unit = {
    println("testing qelim on: ")
//    P.print_fol_formula(fm);
    println();
    val fm1 = if(func == 0) real_elim(fm) else real_elim2(fm);
    println("\nresult of qelim: ");
//    P.print_fol_formula(fm1);
//    println("\n here's a simplified verion:");
//    P.print_fol_formula(simplify_terms(fm1));
    println("\n-------------------");
  }


//  def test_qelim_s(func: Int, s:String): Unit = {
//    test_qelim(func, P.parse(s));
//  }


}




  

  


