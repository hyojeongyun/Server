package com.example.mummoomserver.domain.Post.dto;

import com.example.mummoomserver.domain.Post.Post;

import com.example.mummoomserver.login.users.User;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PostSaveRequestDto {
    private String title;
    private String content;
    private User id;
    private String imgUrl;


    @Builder
    public PostSaveRequestDto(String title, String content, String imgUrl){
        this.title = title;
        this.content = content;
        //this.userIdx = userIdx;   // 나중에 주석 풀 예정
        this.imgUrl = imgUrl;
    }

    public Post toEntity(){
        return Post.builder()
                .title(title)
                .content(content)
                .id(id)
                //.userIdx(userIdx)
                .imgUrl(imgUrl)
                .build();
    }
}
