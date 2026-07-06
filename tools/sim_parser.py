#!/usr/bin/env python3
"""Simulation Python du lexer KrakenLexer + de la grammaire Kraken.bnf (semantique PEG)."""
import sys, re

KEYWORDS = {
 'namespace':'NAMESPACE_KW','include':'INCLUDE_KW','import':'IMPORT_KW','from':'FROM_KW',
 'rule':'RULE_KW','rules':'RULES_KW','on':'ON_KW','context':'CONTEXT_KW','contexts':'CONTEXTS_KW',
 'system':'SYSTEM_KW','root':'ROOT_KW','external':'EXTERNAL_KW','externalcontext':'EXTERNAL_CONTEXT_KW',
 'externalentity':'EXTERNAL_ENTITY_KW','child':'CHILD_KW','is':'IS_KW','entrypoint':'ENTRYPOINT_KW',
 'entrypoints':'ENTRYPOINTS_KW','when':'WHEN_KW','assert':'ASSERT_KW','set':'SET_KW','default':'DEFAULT_KW',
 'reset':'RESET_KW','to':'TO_KW','mandatory':'MANDATORY_KW','empty':'EMPTY_KW','disabled':'DISABLED_KW',
 'hidden':'HIDDEN_KW','matches':'MATCHES_KW','size':'SIZE_KW','min':'MIN_KW','max':'MAX_KW',
 'length':'LENGTH_KW','number':'NUMBER_KW','step':'STEP_KW','in':'IN_KW','overridable':'OVERRIDABLE_KW',
 'error':'ERROR_KW','warn':'WARN_KW','info':'INFO_KW','dimension':'DIMENSION_KW','function':'FUNCTION_KW',
 'priority':'PRIORITY_KW','description':'DESCRIPTION_KW','notstrict':'NOT_STRICT_KW',
 'forbidtarget':'FORBID_TARGET_KW','forbidreference':'FORBID_REFERENCE_KW','serversideonly':'SERVER_SIDE_ONLY_KW',
 'true':'TRUE_KW','false':'FALSE_KW','null':'NULL_KW',
}
KEYWORDS.update({'and':'AND_KW','or':'OR_KW','not':'NOT_KW','if':'IF_KW','then':'THEN_KW',
 'else':'ELSE_KW','for':'FOR_KW','every':'EVERY_KW','some':'SOME_KW','return':'RETURN_KW',
 'this':'THIS_KW','instanceof':'INSTANCEOF_KW','typeof':'TYPEOF_KW','satisfies':'SATISFIES_KW'})
SINGLE = {'{':'LBRACE','}':'RBRACE','(':'LPAREN',')':'RPAREN','[':'LBRACKET',']':'RBRACKET',
          ',':'COMMA','.':'DOT',':':'COLON','@':'AT','*':'STAR','<':'LT','>':'GT','/':'OP'}
OPCHARS = set('+-=!?|&%^~')
DATE_RE = re.compile(r'\d{4}-\d{2}-\d{2}(T\d{2}:\d{2}:\d{2}Z?)?')

def lex(s):
    toks=[]; i=0; n=len(s)
    while i<n:
        c=s[i]
        if c.isspace(): i+=1; continue
        if c=='/' and i+1<n and s[i+1]=='/':
            while i<n and s[i] not in '\r\n': i+=1
            continue
        if c=='/' and i+1<n and s[i+1]=='*':
            j=i+2
            while j<n-1 and not(s[j]=='*' and s[j+1]=='/'): j+=1
            i=j+2 if j<n-1 else n
            continue
        if c in '"\'':
            j=i+1
            while j<n:
                if s[j]=='\\': j+=2; continue
                if s[j]==c: j+=1; break
                j+=1
            toks.append(('STRING',s[i:j])); i=j; continue
        if c.isdigit():
            m=DATE_RE.match(s,i)
            if m and m.group(0).count('-')==2:
                toks.append(('NUMBER_LIT',m.group(0))); i=m.end(); continue
            j=i+1
            while j<n and s[j].isdigit(): j+=1
            if j<n and s[j]=='.' and j+1<n and s[j+1].isdigit():
                j+=1
                while j<n and s[j].isdigit(): j+=1
            toks.append(('NUMBER_LIT',s[i:j])); i=j; continue
        if c.isalpha() or c=='_':
            j=i+1
            while j<n and (s[j].isalnum() or s[j]=='_'): j+=1
            w=s[i:j]
            toks.append((KEYWORDS.get(w.lower(),'IDENTIFIER'),w)); i=j; continue
        if c=='*' and i+1<n and s[i+1]=='*':
            toks.append(('OP','**')); i+=2; continue
        if c in SINGLE:
            toks.append((SINGLE[c],c)); i+=1; continue
        if c=='?' and i+1<n and s[i+1]=='.':
            toks.append(('QDOT','?.')); i+=2; continue
        if c=='?' and i+1<n and s[i+1]=='[':
            toks.append(('QLBRACKET','?[')); i+=2; continue
        if c in OPCHARS:
            j=i+1
            while j<n and s[j] in OPCHARS: j+=1
            toks.append(('OP',s[i:j])); i=j; continue
        toks.append(('BAD',c)); i+=1
    return toks

class S:
    def __init__(self,toks): self.t=toks; self.p=0; self.maxp=0
    def peek(self):
        return self.t[self.p][0] if self.p<len(self.t) else 'EOF'

def tk(name):
    def f(s):
        if s.peek()==name:
            s.p+=1; s.maxp=max(s.maxp,s.p); return True
        return False
    return f

def seq(*ps):
    def f(s):
        save=s.p
        for p in ps:
            if not p(s): s.p=save; return False
        return True
    return f

def alt(*ps):
    def f(s):
        for p in ps:
            if p(s): return True
        return False
    return f

def opt(p):
    def f(s):
        p(s); return True
    return f

def many(p):
    def f(s):
        while True:
            save=s.p
            if not p(s) or s.p==save:
                s.p=save; return True
    return f

def many1(p):
    def f(s):
        if not p(s): return False
        return many(p)(s)
    return f

def not_(p):
    def f(s):
        save=s.p
        r=p(s)
        s.p=save
        return not r
    return f

def ref(name):
    def f(s): return RULES[name](s)
    return f

R=ref
RULES={}
def define(name,p): RULES[name]=p

# ---- identifiants ----
define('expr_id', alt(*[tk(t) for t in ['IDENTIFIER','ON_KW','FROM_KW','TO_KW','MIN_KW','MAX_KW','STEP_KW','SIZE_KW','LENGTH_KW',
 'NUMBER_KW','EMPTY_KW','MANDATORY_KW','DISABLED_KW','HIDDEN_KW','DESCRIPTION_KW','PRIORITY_KW','EXTERNAL_KW',
 'CHILD_KW','ROOT_KW','SYSTEM_KW','CONTEXT_KW','DIMENSION_KW','FUNCTION_KW','MATCHES_KW','INCLUDE_KW','NAMESPACE_KW']]))
define('id', alt(R('expr_id'),tk('ERROR_KW'),tk('WARN_KW'),tk('INFO_KW')))

# ---- expressions (KEL structuré, v0.3) ----
define('literal', alt(tk('STRING'),tk('NUMBER_LIT'),tk('TRUE_KW'),tk('FALSE_KW'),tk('NULL_KW')))
define('call_args', seq(tk('LPAREN'),opt(seq(R('expression'),many(seq(tk('COMMA'),R('expression'))))),tk('RPAREN')))
define('function_call', seq(R('expr_id'),R('call_args')))
define('sev_ref', seq(not_(R('payload_message')),R('message_severity')))
define('group_expr', seq(tk('LPAREN'),opt(R('expression')),tk('RPAREN')))
define('collection_lit', seq(tk('LBRACE'),opt(seq(R('expression'),many(seq(tk('COMMA'),R('expression'))))),tk('RBRACE')))
define('primary_expr', alt(R('literal'),tk('THIS_KW'),R('function_call'),R('sev_ref'),R('expr_id'),
 R('group_expr'),R('collection_lit')))
define('path_segment', seq(R('id'),opt(R('call_args'))))
define('bracket_access', seq(alt(tk('LBRACKET'),tk('QLBRACKET')),opt(alt(tk('STAR'),R('expression'))),tk('RBRACKET')))
define('postfix_part', alt(seq(alt(tk('DOT'),tk('QDOT')),R('path_segment')),R('bracket_access')))
define('postfix_expr', seq(R('primary_expr'),many(R('postfix_part'))))
define('prefix_op', alt(tk('OP'),tk('NOT_KW')))
define('simple_operand', seq(many(R('prefix_op')),R('postfix_expr')))
define('if_expr', seq(tk('IF_KW'),R('expression'),tk('THEN_KW'),R('expression'),opt(seq(tk('ELSE_KW'),R('expression')))))
define('for_expr', seq(tk('FOR_KW'),R('id'),tk('IN_KW'),R('expression'),tk('RETURN_KW'),R('expression')))
define('quantifier_expr', seq(alt(tk('EVERY_KW'),tk('SOME_KW')),R('id'),
 opt(seq(tk('IN_KW'),R('expression'))),opt(seq(tk('SATISFIES_KW'),R('expression')))))
define('expr_operand', alt(R('if_expr'),R('for_expr'),R('quantifier_expr'),R('simple_operand')))
define('binary_op', alt(tk('OP'),tk('LT'),tk('GT'),tk('STAR'),tk('COLON'),tk('IN_KW'),tk('IS_KW'),
 tk('AND_KW'),tk('OR_KW'),tk('INSTANCEOF_KW'),tk('TYPEOF_KW'),tk('SATISFIES_KW'),tk('MATCHES_KW')))
define('value_chain', seq(R('expr_operand'),many(seq(R('binary_op'),R('expr_operand')))))
define('set_var', seq(not_(seq(tk('SET_KW'),R('set_kind'))),tk('SET_KW'),R('id'),opt(tk('TO_KW')),R('value_chain')))
define('expression', alt(
 seq(many1(R('set_var')),opt(seq(tk('RETURN_KW'),R('value_chain')))),
 seq(tk('RETURN_KW'),R('value_chain')),
 R('value_chain')))
# compat : nav_value utilise collection_lit désormais (alias brace_expr)
define('brace_expr', R('collection_lit'))

# ---- header ----
define('qualified_name', seq(R('id'),many(seq(tk('DOT'),R('id')))))
define('namespace_decl', seq(tk('NAMESPACE_KW'),R('qualified_name')))
define('include_decl', seq(tk('INCLUDE_KW'),R('qualified_name')))
define('import_rule_names', seq(tk('STRING'),many(seq(tk('COMMA'),tk('STRING')))))
define('rule_import_decl', seq(tk('IMPORT_KW'),tk('RULE_KW'),R('import_rule_names'),tk('FROM_KW'),R('qualified_name')))
define('import_decl', alt(R('include_decl'),R('rule_import_decl')))

# ---- annotations ----
define('signed_number', seq(opt(tk('OP')),tk('NUMBER_LIT')))
define('annotation_arg', alt(tk('STRING'),R('signed_number'),tk('TRUE_KW'),tk('FALSE_KW'),R('qualified_name')))
define('dimension_annotation', seq(tk('DIMENSION_KW'),tk('LPAREN'),R('annotation_arg'),tk('COMMA'),R('annotation_arg'),tk('RPAREN')))
define('annotation_body', alt(R('dimension_annotation'),tk('NOT_STRICT_KW'),tk('SERVER_SIDE_ONLY_KW'),
 tk('FORBID_TARGET_KW'),tk('FORBID_REFERENCE_KW'),R('id')))
define('annotation', seq(tk('AT'),R('annotation_body')))

# ---- contexts ----
define('path_expr', seq(R('id'),many(seq(tk('DOT'),R('id')))))
define('nav_value', alt(R('brace_expr'),R('path_expr')))
define('inherited_contexts', seq(tk('IS_KW'),R('id'),many(seq(tk('COMMA'),R('id')))))
define('child_decl', seq(many(R('annotation')),tk('CHILD_KW'),opt(tk('STAR')),R('id'),opt(seq(tk('COLON'),R('nav_value')))))
define('field_decl', seq(many(R('annotation')),opt(tk('EXTERNAL_KW')),R('id'),opt(tk('STAR')),R('id'),opt(seq(tk('COLON'),R('nav_value')))))
define('context_member', alt(R('child_decl'),R('field_decl')))
define('context_decl', seq(many(R('annotation')),opt(tk('ROOT_KW')),opt(tk('SYSTEM_KW')),tk('CONTEXT_KW'),R('id'),
 opt(R('inherited_contexts')),tk('LBRACE'),many(R('context_member')),tk('RBRACE')))
define('contexts_member', alt(R('contexts_block'),R('context_decl')))
define('contexts_block', seq(tk('CONTEXTS_KW'),tk('LBRACE'),many(R('contexts_member')),tk('RBRACE')))
define('external_context_value', alt(seq(tk('LBRACE'),opt(R('external_context_items')),tk('RBRACE')),R('id')))
define('external_context_item', seq(R('id'),tk('COLON'),R('external_context_value')))
define('external_context_items', seq(R('external_context_item'),many(seq(tk('COMMA'),R('external_context_item')))))
define('external_context_decl', seq(tk('EXTERNAL_CONTEXT_KW'),tk('LBRACE'),opt(R('external_context_items')),tk('RBRACE')))
define('external_field_decl', seq(R('id'),opt(tk('STAR')),R('id')))
define('external_entity_decl', seq(tk('EXTERNAL_ENTITY_KW'),R('id'),tk('LBRACE'),many(R('external_field_decl')),tk('RBRACE')))

# ---- rules ----
define('rule_name', tk('STRING'))
define('rule_target', seq(tk('ON_KW'),R('id'),opt(seq(tk('DOT'),R('path_expr')))))
define('description_clause', seq(tk('DESCRIPTION_KW'),tk('STRING')))
define('priority_value', alt(tk('MIN_KW'),tk('MAX_KW'),R('signed_number')))
define('priority_clause', seq(tk('PRIORITY_KW'),R('priority_value')))
define('when_clause', seq(tk('WHEN_KW'),R('expression')))
define('message_severity', alt(tk('ERROR_KW'),tk('WARN_KW'),tk('INFO_KW')))
define('payload_message', seq(R('message_severity'),tk('STRING'),opt(seq(tk('COLON'),tk('STRING')))))
define('override_clause', seq(tk('OVERRIDABLE_KW'),opt(tk('STRING'))))
define('assert_suffix', alt(seq(R('payload_message'),opt(R('override_clause'))),R('override_clause')))
define('set_kind', alt(tk('MANDATORY_KW'),tk('DISABLED_KW'),tk('HIDDEN_KW')))
define('set_payload', seq(tk('SET_KW'),R('set_kind'),opt(R('payload_message')),opt(R('override_clause'))))
define('default_kind', alt(tk('DEFAULT_KW'),tk('RESET_KW')))
define('default_payload', seq(R('default_kind'),tk('TO_KW'),R('expression')))
define('empty_assert', seq(tk('EMPTY_KW'),opt(R('assert_suffix'))))
define('matches_assert', seq(tk('MATCHES_KW'),tk('STRING'),opt(R('assert_suffix'))))
define('length_assert', seq(tk('LENGTH_KW'),tk('NUMBER_LIT'),opt(R('assert_suffix'))))
define('size_spec', alt(seq(tk('MIN_KW'),tk('NUMBER_LIT'),tk('MAX_KW'),tk('NUMBER_LIT')),
 seq(tk('MIN_KW'),tk('NUMBER_LIT')),seq(tk('MAX_KW'),tk('NUMBER_LIT')),tk('NUMBER_LIT')))
define('size_assert', seq(tk('SIZE_KW'),R('size_spec'),opt(R('assert_suffix'))))
define('min_bound', seq(tk('MIN_KW'),R('signed_number')))
define('max_bound', seq(tk('MAX_KW'),R('signed_number')))
define('step_bound', seq(tk('STEP_KW'),R('signed_number')))
define('number_spec', alt(seq(R('min_bound'),opt(R('max_bound')),opt(R('step_bound'))),
 seq(R('max_bound'),opt(R('step_bound')))))
define('number_assert', seq(tk('NUMBER_KW'),R('number_spec'),opt(R('assert_suffix'))))
define('value_item', alt(tk('STRING'),R('signed_number')))
define('value_list', seq(R('value_item'),many(seq(tk('COMMA'),R('value_item')))))
define('in_assert', seq(tk('IN_KW'),R('value_list'),opt(R('assert_suffix'))))
define('expr_assert', seq(R('expression'),opt(R('assert_suffix'))))
define('assert_kind', alt(R('empty_assert'),R('matches_assert'),R('length_assert'),R('size_assert'),
 R('number_assert'),R('in_assert'),R('expr_assert')))
define('assert_payload', seq(tk('ASSERT_KW'),R('assert_kind')))
define('rule_clause', alt(R('description_clause'),R('priority_clause'),R('when_clause'),R('set_payload'),
 R('default_payload'),R('assert_payload'),R('payload_message'),R('override_clause')))
define('rule_body', seq(tk('LBRACE'),many(R('rule_clause')),tk('RBRACE')))
define('rule_decl', seq(many(R('annotation')),tk('RULE_KW'),opt(R('rule_name')),opt(R('rule_target')),R('rule_body')))
define('rules_member', alt(R('rules_block'),R('rule_decl')))
define('rules_block', seq(many(R('annotation')),tk('RULES_KW'),tk('LBRACE'),many(R('rules_member')),tk('RBRACE')))

# ---- entry points ----
define('ep_name', tk('STRING'))
define('ep_ref', seq(tk('ENTRYPOINT_KW'),tk('STRING')))
define('rule_ref', tk('STRING'))
define('entry_point_item', alt(R('ep_ref'),R('rule_ref')))
define('entry_point_items', seq(R('entry_point_item'),many(seq(tk('COMMA'),R('entry_point_item')))))
define('entry_point_decl', seq(many(R('annotation')),tk('ENTRYPOINT_KW'),opt(R('ep_name')),tk('LBRACE'),
 opt(R('entry_point_items')),tk('RBRACE')))
define('ep_block_member', alt(R('entry_points_block'),R('entry_point_decl')))
define('entry_points_block', seq(many(R('annotation')),tk('ENTRYPOINTS_KW'),tk('LBRACE'),many(R('ep_block_member')),tk('RBRACE')))

# ---- dimension / function ----
define('dimension_decl', seq(tk('DIMENSION_KW'),tk('STRING'),tk('COLON'),R('id')))
define('type_ref', seq(R('id'),opt(seq(tk('LT'),R('type_ref'),many(seq(tk('COMMA'),R('type_ref'))),tk('GT'))),
 opt(seq(tk('LBRACKET'),tk('RBRACKET')))))
define('generic_bound', seq(R('id'),tk('IS_KW'),R('type_ref')))
define('generic_bounds', seq(tk('LT'),R('generic_bound'),many(seq(tk('COMMA'),R('generic_bound'))),tk('GT')))
define('function_param', seq(R('type_ref'),opt(R('id'))))
define('function_params', seq(R('function_param'),many(seq(tk('COMMA'),R('function_param')))))
define('return_type', seq(tk('COLON'),R('type_ref')))
define('function_body', seq(tk('LBRACE'),R('expression'),tk('RBRACE')))
define('function_decl', seq(tk('FUNCTION_KW'),opt(R('generic_bounds')),R('id'),tk('LPAREN'),
 opt(R('function_params')),tk('RPAREN'),opt(R('return_type')),opt(R('function_body'))))

define('model_item', alt(R('contexts_block'),R('context_decl'),R('external_context_decl'),R('external_entity_decl'),
 R('rules_block'),R('rule_decl'),R('entry_points_block'),R('entry_point_decl'),R('dimension_decl'),R('function_decl')))
define('kraken_file', seq(opt(R('namespace_decl')),many(R('import_decl')),many(R('model_item'))))

def parse_file(path):
    src=open(path,encoding='utf-8').read()
    toks=lex(src)
    bad=[t for t in toks if t[0]=='BAD']
    s=S(toks)
    ok=RULES['kraken_file'](s)
    full = ok and s.p==len(toks)
    status='OK' if (full and not bad) else 'ECHEC'
    print(f"{status}  {path}  ({len(toks)} tokens)")
    if bad: print("   tokens invalides:",bad[:5])
    if not full:
        i=max(s.p,s.maxp)
        ctx=' '.join(t[1] for t in toks[max(0,i-6):i+6])
        print(f"   bloque au token #{i}: ...{ctx}...")
    return full and not bad

if __name__=='__main__':
    import os
    root=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    files=sys.argv[1:] or [root+'/src/test/testData/parser/Full.rules',
           root+'/src/test/testData/parser/Contexts.rules',
           root+'/src/test/testData/parser/Header.rules',
           root+'/examples/demo.rules']
    results=[parse_file(f) for f in files]
    sys.exit(0 if all(results) else 1)
