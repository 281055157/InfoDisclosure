-- A feedback must reference the rule that produced the issue, not the issue category.
-- Repair only unprocessed false-positive feedback so historic governance cases can be grouped safely.
update review_rule_feedback
set rule_code = (
        select issue.rule_code
        from review_issue issue
        where issue.id = review_rule_feedback.issue_id
    ),
    rule_version_id = coalesce((
        select issue.rule_version_id
        from review_issue issue
        where issue.id = review_rule_feedback.issue_id
    ), rule_version_id),
    rule_execution_id = coalesce((
        select issue.rule_execution_id
        from review_issue issue
        where issue.id = review_rule_feedback.issue_id
    ), rule_execution_id),
    aggregation_key = (
        select coalesce(issue.rule_code, '') || '|' ||
               coalesce(cast(issue.rule_version_id as varchar(32)), '') || '|' ||
               coalesce(review_rule_feedback.feedback_type, '') || '|' ||
               coalesce(review_rule_feedback.document_category, '') || '|' ||
               coalesce(review_rule_feedback.declared_document_type, '') || '|' ||
               coalesce(issue.issue_code, '')
        from review_issue issue
        where issue.id = review_rule_feedback.issue_id
    )
where feedback_type = 'FALSE_POSITIVE'
  and process_status in ('NEW', 'PENDING')
  and issue_id is not null
  and exists (
      select 1
      from review_issue issue
      where issue.id = review_rule_feedback.issue_id
        and issue.rule_code is not null
        and trim(issue.rule_code) <> ''
  );

alter table rule_governance_run
    add column if not exists skipped_feedback_count integer not null default 0;

alter table rule_governance_run
    add column if not exists skip_reason_summary text;
