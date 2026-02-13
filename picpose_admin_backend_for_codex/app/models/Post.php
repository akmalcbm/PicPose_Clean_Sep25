<?php
class Post {
    public $id;
    public $title;
    public $content;
    public $imageUrl;
    public $category_id;
    public $tags; // array
    public $type;
    public $short_description;
    public $prompt_text;
    public $status;
    public $priority;
    public $likes;
    public $is_popular;
    public $created_at;
    public $updated_at;

    public static function fromRow($row) {
        $p = new Post();
        $p->id = $row['id'];
        $p->title = $row['title'];
        $p->content = $row['content'];
        $p->imageUrl = $row['image_url1'] ?? '';
        $p->category_id = $row['category_id'];
        $p->tags = !empty($row['tags']) ? (json_decode($row['tags'], true) ?: array_filter(array_map('trim', explode(',', $row['tags'])))) : [];
        $p->type = $row['type'];
        $p->short_description = $row['short_description'];
        $p->prompt_text = $row['prompt_text'];
        $p->status = $row['status'];
        $p->priority = (int)$row['priority'];
        $p->likes = (int)$row['likes'];
        $p->is_popular = (bool)$row['is_popular'];
        $p->created_at = $row['created_at'];
        $p->updated_at = $row['updated_at'];
        return $p;
    }
}
